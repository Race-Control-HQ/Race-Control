import Foundation

/// Networking layer for the RaceControl FastF1 backend.
///
/// The App Store build always uses the production RaceControl backend. Self-hosted
/// builds can point elsewhere by changing `AppConfig.apiBaseURL` before building.
actor APIClient {
    static let shared = APIClient()

    private let session: URLSessionProtocol
    private let decoder = JSONDecoder()

    /// Builds the production-tuned session used when no transport is
    /// injected (i.e. by `shared`). Longer timeouts than `.default` since
    /// FastF1 loads can be slow.
    private static func makeDefaultSession() -> URLSessionProtocol {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 45
        config.timeoutIntervalForResource = 90
        config.waitsForConnectivity = true
        return URLSession(configuration: config)
    }

    /// `session` defaults to the production-tuned `URLSession` so
    /// `static let shared = APIClient()` keeps working unchanged; tests can
    /// inject a mock conforming to `URLSessionProtocol` instead.
    init(session: URLSessionProtocol = APIClient.makeDefaultSession()) {
        self.session = session
    }

    var baseURL: URL {
        AppConfig.apiBaseURL
    }

    func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        // Build the URL by string so query strings (`?d1=…`) are preserved;
        // URL.append(path:) would percent-encode the `?`.
        var base = baseURL.absoluteString
        if base.hasSuffix("/") { base.removeLast() }
        let prefix = path.hasPrefix("/") ? "" : "/"
        guard let url = URL(string: base + prefix + path) else {
            throw APIError.invalidResponse
        }

        // First attempt, then one retry with a fresh App Attest token on 401
        // (covers a token that expired or a server key-store reset).
        do {
            return try await perform(url: url, as: type)
        } catch APIError.server(let status, _) where status == 401 && usingAppAttest {
            await AppAttestService.shared.invalidateToken()
            return try await perform(url: url, as: type)
        }
    }

    private func perform<T: Decodable>(url: URL, as type: T.Type) async throws -> T {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let header = await authorizationHeader() {
            request.setValue(header, forHTTPHeaderField: "Authorization")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }
            guard (200..<300).contains(http.statusCode) else {
                let detail = (try? decoder.decode(APIErrorBody.self, from: data))?.detail
                throw APIError.server(status: http.statusCode, detail: detail)
            }
            return try decoder.decode(T.self, from: data)
        } catch let error as APIError {
            throw error
        } catch let error as DecodingError {
            throw APIError.decoding(error)
        } catch {
            throw APIError.transport(error)
        }
    }

    /// True when we're relying on App Attest (no manual token set).
    private var usingAppAttest: Bool { Keychain.apiToken.isEmpty }

    /// Auth precedence: manual admin token (dev/break-glass) → App Attest token
    /// → nothing (local dev server with auth disabled).
    private func authorizationHeader() async -> String? {
        let manual = Keychain.apiToken
        if !manual.isEmpty { return "Bearer \(manual)" }
        if let token = await AppAttestService.shared.bearerToken(baseURL: baseURL) {
            return "Bearer \(token)"
        }
        return nil
    }
}

// MARK: - Endpoints
extension APIClient {
    func seasons() async throws -> [Int] {
        try await get("api/seasons", as: [Int].self)
    }
    func schedule(year: Int) async throws -> [RaceEvent] {
        try await get("api/schedule/\(year)", as: [RaceEvent].self)
    }
    func results(year: Int, round: Int, session: String) async throws -> SessionResultsResponse {
        try await get("api/results/\(year)/\(round)/\(session)", as: SessionResultsResponse.self)
    }
    func driverStandings(year: Int) async throws -> [DriverStanding] {
        try await get("api/standings/drivers/\(year)", as: [DriverStanding].self)
    }
    func constructorStandings(year: Int) async throws -> [ConstructorStanding] {
        try await get("api/standings/constructors/\(year)", as: [ConstructorStanding].self)
    }
    func drivers(year: Int) async throws -> [Driver] {
        try await get("api/drivers/\(year)", as: [Driver].self)
    }
    func driverDetail(year: Int, driverId: String) async throws -> DriverDetail {
        try await get("api/drivers/\(year)/\(driverId)", as: DriverDetail.self)
    }
    func teams(year: Int) async throws -> [Team] {
        try await get("api/teams/\(year)", as: [Team].self)
    }
    func teamDetail(year: Int, teamId: String) async throws -> Team {
        try await get("api/teams/\(year)/\(teamId)", as: Team.self)
    }
    func circuits(year: Int) async throws -> [Circuit] {
        try await get("api/circuits/\(year)", as: [Circuit].self)
    }
    func circuitMap(year: Int, round: Int) async throws -> CircuitMap {
        try await get("api/circuit/\(year)/\(round)", as: CircuitMap.self)
    }
    func replay(year: Int, round: Int) async throws -> RaceReplay {
        try await get("api/replay/\(year)/\(round)", as: RaceReplay.self)
    }
    func replayPositions(year: Int, round: Int) async throws -> ReplayPositions {
        try await get("api/replay-positions/\(year)/\(round)", as: ReplayPositions.self)
    }
    func lapTimes(year: Int, round: Int) async throws -> LapTimesResponse {
        try await get("api/laptimes/\(year)/\(round)", as: LapTimesResponse.self)
    }
    func strategy(year: Int, round: Int) async throws -> StrategyResponse {
        try await get("api/strategy/\(year)/\(round)", as: StrategyResponse.self)
    }
    func weather(year: Int, round: Int, session: String) async throws -> WeatherResponse {
        try await get("api/weather/\(year)/\(round)/\(session)", as: WeatherResponse.self)
    }
    func raceDrivers(year: Int, round: Int) async throws -> [RaceDriver] {
        try await get("api/racedrivers/\(year)/\(round)", as: [RaceDriver].self)
    }
    func telemetry(year: Int, round: Int, driver: String, lap: String = "fastest") async throws -> TelemetryResponse {
        try await get("api/telemetry/\(year)/\(round)/\(driver)?lap=\(lap)", as: TelemetryResponse.self)
    }
    func telemetryCompare(year: Int, round: Int, d1: String, d2: String) async throws -> TelemetryCompareResponse {
        try await get("api/telemetry-compare/\(year)/\(round)?d1=\(d1)&d2=\(d2)", as: TelemetryCompareResponse.self)
    }
    func retirements(year: Int, round: Int) async throws -> RetirementsResponse {
        try await get("api/retirements/\(year)/\(round)", as: RetirementsResponse.self)
    }
    func flags(year: Int, round: Int, session: String = "R") async throws -> FlagsResponse {
        try await get("api/flags/\(year)/\(round)?session=\(session)", as: FlagsResponse.self)
    }
    func raceControl(year: Int, round: Int, session: String = "R") async throws -> RaceControlResponse {
        try await get("api/racecontrol/\(year)/\(round)?session=\(session)", as: RaceControlResponse.self)
    }
    func penalties(year: Int, round: Int, session: String = "R") async throws -> PenaltiesResponse {
        try await get("api/penalties/\(year)/\(round)?session=\(session)", as: PenaltiesResponse.self)
    }
    func reliability(year: Int) async throws -> ReliabilityResponse {
        try await get("api/reliability/\(year)", as: ReliabilityResponse.self)
    }
    func compare(year: Int, d1: String, d2: String) async throws -> CompareResponse {
        try await get("api/compare/\(year)/\(d1)/\(d2)", as: CompareResponse.self)
    }
    func standingsEvolution(year: Int) async throws -> StandingsEvolution {
        try await get("api/standings-evolution/\(year)", as: StandingsEvolution.self)
    }
    func wdcCalculator(year: Int, throughRound: Int? = nil) async throws -> WdcCalculator {
        let path = "api/wdc-calculator/\(year)" + (throughRound.map { "?through_round=\($0)" } ?? "")
        return try await get(path, as: WdcCalculator.self)
    }
    func raceTrace(year: Int, round: Int, mode: String = "median") async throws -> RaceTraceResponse {
        try await get("api/race-trace/\(year)/\(round)?mode=\(mode)", as: RaceTraceResponse.self)
    }
    func tyrePerformance(year: Int, round: Int) async throws -> TyrePerformanceResponse {
        try await get("api/tyre-performance/\(year)/\(round)", as: TyrePerformanceResponse.self)
    }
    func pitStops(year: Int, round: Int) async throws -> PitStopsResponse {
        try await get("api/pit-stops/\(year)/\(round)", as: PitStopsResponse.self)
    }
    func qualifyingSectors(year: Int, round: Int) async throws -> QualifyingSectorsResponse {
        try await get("api/qualifying-sectors/\(year)/\(round)", as: QualifyingSectorsResponse.self)
    }
    func miniSectors(year: Int, round: Int, session: String = "Q", top: Int = 10) async throws -> MiniSectorsResponse {
        try await get(
            "api/minisectors/\(year)/\(round)?session=\(session)&top=\(top)",
            as: MiniSectorsResponse.self
        )
    }
    func titleScenarios(
        year: Int,
        d1: String? = nil,
        d2: String? = nil,
        throughRound: Int? = nil
    ) async throws -> TitleScenariosResponse {
        var query: [String] = []
        if let d1 { query.append("d1=\(d1)") }
        if let d2 { query.append("d2=\(d2)") }
        if let throughRound { query.append("through_round=\(throughRound)") }
        let suffix = query.isEmpty ? "" : "?\(query.joined(separator: "&"))"
        return try await get("api/title-scenarios/\(year)\(suffix)", as: TitleScenariosResponse.self)
    }
    func driverFingerprint(year: Int, driverId: String) async throws -> DriverFingerprintResponse {
        try await get("api/driver-fingerprint/\(year)/\(driverId)", as: DriverFingerprintResponse.self)
    }
}

// MARK: - Errors & config
enum APIError: LocalizedError {
    case invalidResponse
    case server(status: Int, detail: String?)
    case decoding(DecodingError)
    case transport(Error)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "The server returned an unexpected response."
        case .server(let status, let detail):
            if status == 401 {
                return "The server couldn't authenticate this app. Please try again."
            }
            return detail ?? "Server error (\(status))."
        case .decoding:
            return "Couldn't read the data from the server."
        case .transport(let error):
            let ns = error as NSError
            if ns.domain == NSURLErrorDomain {
                return "Can't reach the RaceControl server. Please try again later."
            }
            return error.localizedDescription
        }
    }
}

private struct APIErrorBody: Decodable { let detail: String? }

enum AppConfig {
    private static let productionURL = URL(string: "https://racecontrol.owl-media.co.uk")!
    private static let overrideKey = "dev_backend_base_url_override"

    /// The backend the app talks to. Defaults to the production RaceControl
    /// backend; can be pointed at a local dev server (e.g. `./run.sh` on your
    /// Mac, or its LAN IP when testing from a physical device) from Settings
    /// without rebuilding. This is what the `NSAllowsLocalNetworking` ATS
    /// exception in Info.plist exists to support. Previously that exception
    /// was configured but there was no actual way to set the override it
    /// describes, so local dev testing required hand-editing this constant.
    ///
    /// Note: on the Simulator, `http://localhost:8000` reaches the host
    /// Mac directly. On a physical device, `localhost` means the device
    /// itself: use the Mac's LAN IP (e.g. `http://192.168.1.23:8000`)
    /// instead, since both are on the same Wi-Fi network.
    static var apiBaseURL: URL {
        if let raw = UserDefaults.standard.string(forKey: overrideKey),
           !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           let url = URL(string: raw), url.scheme != nil, url.host != nil {
            return url
        }
        return productionURL
    }

    static var isUsingProduction: Bool { apiBaseURL == productionURL }

    static var devBackendOverrideRaw: String {
        UserDefaults.standard.string(forKey: overrideKey) ?? ""
    }

    static func setDevBackendOverride(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            UserDefaults.standard.removeObject(forKey: overrideKey)
        } else {
            UserDefaults.standard.set(trimmed, forKey: overrideKey)
        }
    }
}
