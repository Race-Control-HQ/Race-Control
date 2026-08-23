import XCTest
@testable import RaceControl

/// A scripted stand-in for `URLSession` conforming to `URLSessionProtocol`.
/// Responses are consumed in FIFO order, one per call to `data(for:)`, so a
/// test can script a sequence (e.g. 401 then 200 for the retry path).
actor MockURLSession: URLSessionProtocol {
    enum Stub {
        case response(Data, HTTPURLResponse)
        case failure(Error)
    }

    private var stubs: [Stub]
    private(set) var recordedRequests: [URLRequest] = []

    init(stubs: [Stub]) {
        self.stubs = stubs
    }

    var requestCount: Int { recordedRequests.count }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        recordedRequests.append(request)
        guard !stubs.isEmpty else {
            XCTFail("MockURLSession received more requests than stubs were provided")
            throw URLError(.unknown)
        }
        switch stubs.removeFirst() {
        case .response(let data, let response):
            return (data, response)
        case .failure(let error):
            throw error
        }
    }
}

private func httpResponse(status: Int, url: URL = URL(string: "https://example.com")!) -> HTTPURLResponse {
    HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!
}

final class APIClientTests: XCTestCase {

    override func setUp() {
        super.setUp()
        // `usingAppAttest` (and therefore the 401-retry branch) is keyed off
        // whether a manual admin token is set; make sure tests run with a
        // clean slate regardless of what's left over on this device/simulator.
        Keychain.apiToken = ""
    }

    override func tearDown() {
        Keychain.apiToken = ""
        super.tearDown()
    }

    // MARK: - Happy path

    func testDriverStandingsDecodesSuccessfully() async throws {
        let json = """
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

        let mock = MockURLSession(stubs: [.response(json, httpResponse(status: 200))])
        let client = APIClient(session: mock)

        let standings = try await client.driverStandings(year: 2024)

        XCTAssertEqual(standings.count, 1)
        XCTAssertEqual(standings.first?.driverId, "max_verstappen")
        XCTAssertEqual(standings.first?.fullName, "Max Verstappen")
        XCTAssertEqual(standings.first?.points?.intValue, 227)
        let requestCount = await mock.requestCount
        XCTAssertEqual(requestCount, 1)
    }

    // MARK: - HTTP error status mapping

    func testServerErrorStatusMapsToAPIErrorServer() async {
        let body = #"{"detail": "Season not found"}"#.data(using: .utf8)!
        let mock = MockURLSession(stubs: [.response(body, httpResponse(status: 404))])
        let client = APIClient(session: mock)

        do {
            _ = try await client.driverStandings(year: 1900)
            XCTFail("Expected APIError.server to be thrown")
        } catch let APIError.server(status, detail) {
            XCTAssertEqual(status, 404)
            XCTAssertEqual(detail, "Season not found")
        } catch {
            XCTFail("Expected APIError.server, got \(error)")
        }
    }

    func testServerErrorDescriptionFallsBackWhenNoDetailBody() async {
        let mock = MockURLSession(stubs: [.response(Data(), httpResponse(status: 500))])
        let client = APIClient(session: mock)

        do {
            _ = try await client.seasons()
            XCTFail("Expected an error to be thrown")
        } catch let error as APIError {
            XCTAssertEqual(error.errorDescription, "Server error (500).")
        } catch {
            XCTFail("Expected APIError, got \(error)")
        }
    }

    // MARK: - 401 retry

    /// On a 401, `APIClient.get` invalidates the App Attest token and retries
    /// once. Fully exercising the App Attest half (a real attested key/JWT
    /// round trip) isn't practical/safe to fake in a unit test, so this
    /// verifies the observable contract instead: with no manual token set
    /// (`usingAppAttest == true`), a 401 followed by a 200 results in exactly
    /// two transport calls and an eventual success — i.e. the retry actually
    /// happens and isn't swallowed or doubled.
    func testUnauthorizedResponseTriggersExactlyOneRetry() async throws {
        let json = "[1, 2, 3]".data(using: .utf8)!
        let mock = MockURLSession(stubs: [
            .response(Data(), httpResponse(status: 401)),
            .response(json, httpResponse(status: 200)),
        ])
        let client = APIClient(session: mock)

        let seasons = try await client.seasons()

        XCTAssertEqual(seasons, [1, 2, 3])
        let requestCount = await mock.requestCount
        XCTAssertEqual(requestCount, 2, "Expected the initial request plus exactly one retry after the 401")
    }

    /// A second consecutive 401 (retry also unauthorized) should surface as
    /// an error rather than retry indefinitely.
    func testRepeatedUnauthorizedResponseDoesNotLoopAndSurfacesError() async {
        let mock = MockURLSession(stubs: [
            .response(Data(), httpResponse(status: 401)),
            .response(Data(), httpResponse(status: 401)),
        ])
        let client = APIClient(session: mock)

        do {
            _ = try await client.seasons()
            XCTFail("Expected an error to be thrown")
        } catch let APIError.server(status, _) {
            XCTAssertEqual(status, 401)
        } catch {
            XCTFail("Expected APIError.server(401), got \(error)")
        }
        let requestCount = await mock.requestCount
        XCTAssertEqual(requestCount, 2, "Expected exactly one retry, not an unbounded loop")
    }
}
