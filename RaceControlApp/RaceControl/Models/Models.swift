import Foundation

// MARK: - Schedule / Events

struct RaceEvent: Codable, Identifiable, Hashable {
    let round: Int
    let name: String?
    let officialName: String?
    let country: String?
    let location: String?
    let date: String?
    let format: String?
    let sessions: [EventSession]
    let completed: Bool
    let year: Int

    var id: Int { round }
    var displayName: String { name ?? "Round \(round)" }

    var isSprintWeekend: Bool {
        (format ?? "").lowercased().contains("sprint")
    }

    var parsedDate: Date? { ISO8601.flexible(date) }
}

struct EventSession: Codable, Hashable {
    let name: String?
    let date: String?
    let identifier: String?
}

// MARK: - Results

struct SessionResultsResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let sessionName: String?
    let eventName: String?
    let totalLaps: JSONValue?
    let results: [ResultEntry]
}

struct ResultEntry: Codable, Identifiable, Hashable {
    let position: Double?
    let classifiedPosition: String?
    let driverNumber: String?
    let abbreviation: String?
    let driverId: String?
    let firstName: String?
    let lastName: String?
    let fullName: String?
    let headshotUrl: String?
    let countryCode: String?
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let gridPosition: Double?
    let status: String?
    let points: Double?
    let timeMs: Int?
    let q1: String?
    let q2: String?
    let q3: String?
    let q1Gap: String?
    let q2Gap: String?
    let q3Gap: String?
    /// Practice only: the driver's best lap, its gap to the session's fastest,
    /// and how many laps they ran. Practice results carry no timing of their
    /// own, so the backend rebuilds this from the laps; null everywhere else.
    let bestLap: String?
    let bestLapGap: String?
    let lapsCompleted: Int?

    var id: String { (driverId ?? abbreviation ?? UUID().uuidString) }

    var positionLabel: String {
        if let p = classifiedPosition, !p.isEmpty, Double(p) == nil { return p } // "R", "DNF", etc.
        if let p = position { return String(Int(p)) }
        return "–"
    }

    var pointsLabel: String {
        guard let points, points > 0 else { return "" }
        return points.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(points)) : String(points)
    }

    var gridDelta: Int? {
        guard let g = gridPosition, let p = position, g > 0 else { return nil }
        return Int(g - p) // positive = places gained
    }
}

// MARK: - Standings

struct DriverStanding: Codable, Identifiable, Hashable {
    let position: JSONValue?
    let points: JSONValue?
    let wins: JSONValue?
    let driverId: String?
    let driverNumber: JSONValue?
    let driverCode: String?
    let givenName: String?
    let familyName: String?
    let nationality: String?
    let dateOfBirth: String?
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?

    var id: String { driverId ?? UUID().uuidString }
    var fullName: String { "\(givenName ?? "") \(familyName ?? "")".trimmingCharacters(in: .whitespaces) }
}

struct ConstructorStanding: Codable, Identifiable, Hashable {
    let position: JSONValue?
    let points: JSONValue?
    let wins: JSONValue?
    let teamId: String?
    let teamName: String?
    let nationality: String?
    let teamLogoUrl: String?

    var id: String { teamId ?? UUID().uuidString }
}

// MARK: - Drivers

struct Driver: Codable, Identifiable, Hashable {
    let driverId: String
    let givenName: String?
    let familyName: String?
    let code: String?
    let number: JSONValue?
    let nationality: String?
    let dateOfBirth: String?
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let headshotUrl: String?
    let countryCode: String?
    let position: JSONValue?
    let points: JSONValue?
    let wins: JSONValue?

    var id: String { driverId }
    var fullName: String { "\(givenName ?? "") \(familyName ?? "")".trimmingCharacters(in: .whitespaces) }
    var numberString: String? { number?.stringValue }
    var pointsString: String { points?.numberLabel ?? "0" }
    var positionInt: Int? { position?.intValue }
}

struct DriverDetail: Codable {
    // Mirrors Driver plus season results.
    let driverId: String
    let givenName: String?
    let familyName: String?
    let code: String?
    let number: JSONValue?
    let nationality: String?
    let dateOfBirth: String?
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let headshotUrl: String?
    let countryCode: String?
    let position: JSONValue?
    let points: JSONValue?
    let wins: JSONValue?
    let seasonResults: [DriverSeasonResult]?

    var fullName: String { "\(givenName ?? "") \(familyName ?? "")".trimmingCharacters(in: .whitespaces) }
}

struct DriverSeasonResult: Codable, Identifiable, Hashable {
    let round: Int?
    let raceName: String?
    let position: JSONValue?
    let points: JSONValue?
    let grid: JSONValue?
    let status: String?

    var id: Int { round ?? Int.random(in: 0...99999) }
    var positionInt: Int? { position?.intValue }
    var pointsLabel: String { points?.numberLabel ?? "0" }
}

// MARK: - Teams

struct Team: Codable, Identifiable, Hashable {
    let position: JSONValue?
    let points: JSONValue?
    let wins: JSONValue?
    let teamId: String?
    let teamName: String?
    let nationality: String?
    let teamColor: String?
    let teamLogoUrl: String?
    let drivers: [TeamDriver]

    var id: String { teamId ?? UUID().uuidString }
    var pointsString: String { points?.numberLabel ?? "0" }
    var positionInt: Int? { position?.intValue }
}

struct TeamDriver: Codable, Identifiable, Hashable {
    let driverId: String?
    let name: String?
    let code: String?
    let number: JSONValue?
    let headshotUrl: String?
    let points: JSONValue?

    var id: String { driverId ?? name ?? UUID().uuidString }
    var pointsValue: Double { points?.doubleValue ?? 0 }
    var pointsLabel: String { points?.numberLabel ?? "0" }
}

// MARK: - Circuits

struct Circuit: Codable, Identifiable, Hashable {
    let circuitId: String?
    let name: String?
    let locality: String?
    let country: String?
    let lat: JSONValue?
    let long: JSONValue?

    var id: String { circuitId ?? UUID().uuidString }
}

struct CircuitMap: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let location: String?
    let country: String?
    let outline: [TrackPoint]
    let points: [TrackTelemetryPoint]?
    let corners: [TrackCorner]
    let rotation: Double
    let lengthMeters: Double?
    let minElevation: Double?
    let maxElevation: Double?
    let fastestLap: CircuitFastestLap?
}

/// A point along the fastest lap: position, elevation, speed and DRS state.
struct TrackTelemetryPoint: Codable, Hashable {
    let x: Double
    let y: Double
    let z: Double
    let speed: Double
    let drs: Int
    let distance: Double
    var drsOpen: Bool { [10, 12, 14].contains(drs) }
}

struct CircuitFastestLap: Codable, Hashable {
    let driver: String?
    let driverName: String?
    let team: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let time: String?
    let compound: String?
}

struct TrackPoint: Codable, Hashable {
    let x: Double
    let y: Double
}

struct TrackCorner: Codable, Identifiable, Hashable {
    let number: Int
    let letter: String
    let x: Double
    let y: Double
    /// Turn angle in degrees, when FastF1 provides it.
    let angle: Double?
    /// Distance along the lap (metres) at this corner.
    let distanceMeters: Double?
    /// Approximate speed (km/h) at this corner, cross-referenced from the fastest lap's trace.
    let speed: Double?
    var id: String { "\(number)\(letter)" }
    var label: String { "\(number)\(letter)" }
}

// MARK: - Race replay

struct RaceReplay: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let totalLaps: Int
    let drivers: [ReplayDriver]
    let frames: [ReplayFrame]
}

struct ReplayDriver: Codable, Identifiable, Hashable {
    let code: String
    let driverId: String?
    let fullName: String?
    let teamName: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let number: String?
    var id: String { code }
}

struct ReplayFrame: Codable, Hashable {
    let lap: Int
    let order: [ReplayEntry]
}

struct ReplayEntry: Codable, Identifiable, Hashable {
    let position: Int
    let driver: String
    let driverId: String?
    let teamColor: String?
    let teamName: String?
    let teamLogoUrl: String?
    let lapTimeMs: Int?
    let lapTime: String?
    let compound: String?
    let tyreLife: JSONValue?
    let gapMs: Int?
    let gap: String?

    var id: String { driver }
}

/// Sparse X/Y samples for every car, grouped by race lap.
///
/// The position feed intentionally uses nested arrays on the wire to keep a
/// full race reasonably small: each point is `[x, y]`.
struct ReplayPositions: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let totalLaps: Int
    let drivers: [ReplayDriver]
    let laps: [ReplayLapPositions]
}

struct ReplayLapPositions: Codable {
    let lap: Int
    let positions: [String: [[Double]]]
}
