import Foundation
import Security

/// Minimal Keychain wrapper for the API token.
///
/// Secrets belong in the Keychain, not UserDefaults; UserDefaults is a plist
/// that's trivially readable from a backup or a jailbroken device.
enum Keychain {
    private static let service = "com.owlmedia.racecontrol"

    static func set(_ value: String, for account: String) {
        // Clear any existing entry first: SecItemAdd fails on duplicates.
        delete(account)
        guard !value.isEmpty, let data = value.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            // Available after first unlock; not synced to other devices.
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    static func get(_ account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let string = String(data: data, encoding: .utf8) else { return nil }
        return string
    }

    static func delete(_ account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

extension Keychain {
    static let apiTokenAccount = "api_token"

    static var apiToken: String {
        get { get(apiTokenAccount) ?? "" }
        set { set(newValue, for: apiTokenAccount) }
    }
}
