import XCTest
@testable import RaceControl

/// Vertical smoke test for `StandingsViewModel`: idle -> loading -> loaded,
/// and idle -> loading -> failed, driven end-to-end through a real
/// `APIClient` backed by a mocked transport (`MockURLSession`, defined in
/// APIClientTests.swift) rather than the network.
@MainActor
final class StandingsViewModelTests: XCTestCase {

    private func driverStandingsJSON() -> Data {
        """
        [
          {
            "position": 1,
            "points": 227,
            "wins": 7,
            "driverId": "max_verstappen",
            "driverNumber": 1,
            "driverCode": "VER",
            "givenName": "Max",
            "familyName": "Verstappen",
            "nationality": "Dutch",
            "dateOfBirth": "1997-09-30",
            "teamName": "Red Bull Racing",
            "teamId": "red_bull",
            "teamLogoUrl": null
          }
        ]
        """.data(using: .utf8)!
    }

    private func httpResponse(status: Int) -> HTTPURLResponse {
        HTTPURLResponse(url: URL(string: "https://example.com")!, statusCode: status,
                         httpVersion: nil, headerFields: nil)!
    }

    func testLoadDriversGoesIdleThenLoadingThenLoaded() async {
        let mock = MockURLSession(stubs: [.response(driverStandingsJSON(), httpResponse(status: 200))])
        let vm = StandingsViewModel(apiClient: APIClient(session: mock))

        XCTAssertEqual(vm.driverState, .idle)

        await vm.loadDrivers(year: 2024)

        guard case .loaded(let standings) = vm.driverState else {
            return XCTFail("Expected .loaded, got \(vm.driverState)")
        }
        XCTAssertEqual(standings.count, 1)
        XCTAssertEqual(standings.first?.driverId, "max_verstappen")
    }

    func testLoadDriversGoesIdleThenLoadingThenFailed() async {
        let mock = MockURLSession(stubs: [
            .response(#"{"detail": "Internal server error"}"#.data(using: .utf8)!, httpResponse(status: 500)),
        ])
        let vm = StandingsViewModel(apiClient: APIClient(session: mock))

        XCTAssertEqual(vm.driverState, .idle)

        await vm.loadDrivers(year: 2024)

        guard case .failed(let message) = vm.driverState else {
            return XCTFail("Expected .failed, got \(vm.driverState)")
        }
        XCTAssertEqual(message, "Internal server error")
    }

    /// A second call for the same year while already `.loaded` should be a
    /// no-op (cached), so it must not consume another stub / hit the network.
    func testLoadDriversForSameYearDoesNotReload() async {
        let mock = MockURLSession(stubs: [.response(driverStandingsJSON(), httpResponse(status: 200))])
        let vm = StandingsViewModel(apiClient: APIClient(session: mock))

        await vm.loadDrivers(year: 2024)
        await vm.loadDrivers(year: 2024)

        guard case .loaded = vm.driverState else {
            return XCTFail("Expected .loaded, got \(vm.driverState)")
        }
        let requestCount = await mock.requestCount
        XCTAssertEqual(requestCount, 1, "Second load for the same cached year should not re-hit the transport")
    }
}

// MARK: - Loadable equality for assertions

extension Loadable: @retroactive Equatable where Value: Equatable {
    public static func == (lhs: Loadable<Value>, rhs: Loadable<Value>) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle), (.loading, .loading):
            return true
        case (.loaded(let l), .loaded(let r)):
            return l == r
        case (.failed(let l), .failed(let r)):
            return l == r
        default:
            return false
        }
    }
}
