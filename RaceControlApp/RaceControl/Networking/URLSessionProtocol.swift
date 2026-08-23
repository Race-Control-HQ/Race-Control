import Foundation

/// Narrow seam over `URLSession` so tests can substitute a mock transport
/// without touching `APIClient`'s request-building or error-mapping logic.
protocol URLSessionProtocol: Sendable {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: URLSessionProtocol {}
