import Foundation
import Security

struct Credentials {
    let sessionKey: String
    let organizationId: String

    var isValid: Bool {
        !sessionKey.trimmingCharacters(in: .whitespaces).isEmpty &&
        !organizationId.trimmingCharacters(in: .whitespaces).isEmpty
    }
}

class KeychainManager {
    static let shared = KeychainManager()

    private let service = "com.claudeusage.ClaudeMeter"
    private let sessionKeyAccount = "session_key"
    private let orgIdAccount = "organization_id"

    private init() {}

    func getCredentials() -> Credentials? {
        guard let sessionKey = getString(account: sessionKeyAccount),
              let orgId = getString(account: orgIdAccount) else {
            return nil
        }
        return Credentials(sessionKey: sessionKey, organizationId: orgId)
    }

    func saveCredentials(_ credentials: Credentials) {
        setString(credentials.sessionKey, account: sessionKeyAccount)
        setString(credentials.organizationId, account: orgIdAccount)
    }

    func clearCredentials() {
        deleteString(account: sessionKeyAccount)
        deleteString(account: orgIdAccount)
    }

    func hasCredentials() -> Bool {
        getCredentials()?.isValid == true
    }

    // MARK: - Keychain helpers

    private func getString(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }
        return string
    }

    private func setString(_ value: String, account: String) {
        deleteString(account: account)

        guard let data = value.data(using: .utf8) else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]

        SecItemAdd(query as CFDictionary, nil)
    }

    private func deleteString(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]

        SecItemDelete(query as CFDictionary)
    }
}
