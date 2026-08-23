import Foundation
import DeviceCheck
import CryptoKit

/// Authenticates the app to the backend using Apple App Attest, the App Store
/// way to prove requests come from a genuine, unmodified copy of this app on a
/// real Apple device, with no user-visible API key.
///
/// Lifecycle:
///   • First run: generate a hardware key, attest it with Apple, exchange the
///     attestation for a short-lived JWT (`/attest/verify`).
///   • Thereafter: reuse the cached JWT; when it nears expiry, sign a fresh
///     challenge with the same key (an assertion) to mint a new JWT
///     (`/attest/token`): cheap, no re-attestation.
///   • If the server forgets the key (409), transparently re-attest.
///
/// App Attest is unavailable in the Simulator and on very old devices; in that
/// case `bearerToken()` returns nil and the caller falls back to the manual
/// admin token (Settings) or an unauthenticated request (local dev).
actor AppAttestService {
    static let shared = AppAttestService()

    private let service = DCAppAttestService.shared
    private let session = URLSession(configuration: .ephemeral)
    private let decoder = JSONDecoder()

    private var cachedToken: String?
    private var tokenExpiry: Date?
    /// Serialises concurrent callers so we attest / refresh only once.
    private var inFlight: Task<String?, Never>?

    var isSupported: Bool { service.isSupported }

    /// Returns a valid bearer token, refreshing or attesting as needed, or nil
    /// if App Attest can't be used on this device.
    func bearerToken(baseURL: URL) async -> String? {
        guard service.isSupported else { return nil }

        if let token = cachedToken, let expiry = tokenExpiry, expiry > Date().addingTimeInterval(60) {
            return token
        }
        if let inFlight { return await inFlight.value }

        let task = Task<String?, Never> { await obtainToken(baseURL: baseURL) }
        inFlight = task
        let result = await task.value
        inFlight = nil
        return result
    }

    /// Called by the networking layer on a 401: drops the cached token so the
    /// next request re-mints one.
    func invalidateToken() {
        cachedToken = nil
        tokenExpiry = nil
    }

    // MARK: - Token acquisition
    private func obtainToken(baseURL: URL) async -> String? {
        do {
            // Reuse an existing attested key if we have one; otherwise attest.
            if let keyId = storedKeyId {
                if let token = try await refreshWithAssertion(keyId: keyId, baseURL: baseURL) {
                    return token
                }
            }
            return try await attestNewKey(baseURL: baseURL)
        } catch {
            return nil
        }
    }

    private func attestNewKey(baseURL: URL) async throws -> String? {
        let keyId = try await service.generateKey()
        let challenge = try await fetchChallenge(baseURL: baseURL)
        let clientDataHash = Data(SHA256.hash(data: challengeData(challenge)))
        let attestation = try await service.attestKey(keyId, clientDataHash: clientDataHash)

        let body = [
            "keyId": keyId,
            "attestation": attestation.base64EncodedString(),
            "challenge": challenge,
        ]
        let token = try await post("attest/verify", body: body, baseURL: baseURL)
        storedKeyId = keyId  // persist only after the server accepts it
        return token
    }

    private func refreshWithAssertion(keyId: String, baseURL: URL) async throws -> String? {
        let challenge = try await fetchChallenge(baseURL: baseURL)
        let clientDataHash = Data(SHA256.hash(data: challengeData(challenge)))
        do {
            let assertion = try await service.generateAssertion(keyId, clientDataHash: clientDataHash)
            let body = [
                "keyId": keyId,
                "assertion": assertion.base64EncodedString(),
                "challenge": challenge,
            ]
            return try await post("attest/token", body: body, baseURL: baseURL)
        } catch AttestServiceError.serverRejectedKey {
            // Server lost our key; forget it so we re-attest.
            storedKeyId = nil
            return nil
        }
    }

    // MARK: - HTTP
    private func fetchChallenge(baseURL: URL) async throws -> String {
        var url = baseURL; url.append(path: "attest/challenge")
        let (data, response) = try await session.data(from: url)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw AttestServiceError.badResponse
        }
        return try decoder.decode(ChallengeResponse.self, from: data).challenge
    }

    private func post(_ path: String, body: [String: String], baseURL: URL) async throws -> String {
        var url = baseURL; url.append(path: path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 409 { throw AttestServiceError.serverRejectedKey }
        guard status == 200 else { throw AttestServiceError.badResponse }

        let token = try decoder.decode(TokenResponse.self, from: data)
        cachedToken = token.token
        tokenExpiry = Date().addingTimeInterval(TimeInterval(token.expiresIn))
        return token.token
    }

    // MARK: - Helpers
    /// The server sends the challenge base64-encoded; the bytes it signs over are
    /// those raw bytes, so decode before hashing.
    private func challengeData(_ challenge: String) -> Data {
        Data(base64Encoded: challenge) ?? Data(challenge.utf8)
    }

    /// The attested key id lives in the Keychain (survives reinstall-in-place,
    /// tied to this app + device).
    private var storedKeyId: String? {
        get { Keychain.get(Self.keyIdAccount) }
        set {
            if let newValue { Keychain.set(newValue, for: Self.keyIdAccount) }
            else { Keychain.delete(Self.keyIdAccount) }
        }
    }
    private static let keyIdAccount = "appattest_key_id"
}

private enum AttestServiceError: Error {
    case badResponse
    case serverRejectedKey
}

private struct ChallengeResponse: Decodable { let challenge: String }
private struct TokenResponse: Decodable { let token: String; let expiresIn: Int }
