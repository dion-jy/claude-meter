import Foundation
import Combine

enum UiState {
    case loading
    case loginRequired
    case success(UsageData)
    case error(message: String, isAuthError: Bool)
}

@MainActor
class UsageViewModel: ObservableObject {
    @Published var uiState: UiState = .loading
    @Published var isRefreshing = false
    @Published var lastUpdated: String?

    private let keychain = KeychainManager.shared
    private let api = APIClient.shared
    private var autoRefreshTask: Task<Void, Never>?

    private static let updateIntervalSeconds: TimeInterval = 5 * 60 // 5 minutes

    init() {
        checkCredentialsAndLoad()
    }

    func checkCredentialsAndLoad() {
        Task {
            uiState = .loading
            if keychain.hasCredentials() {
                await fetchUsageData()
            } else {
                uiState = .loginRequired
            }
        }
    }

    func onLoginComplete(sessionKey: String) {
        Task {
            uiState = .loading
            do {
                let orgId = try await api.fetchOrganizationId(sessionKey: sessionKey)
                let credentials = Credentials(sessionKey: sessionKey, organizationId: orgId)
                keychain.saveCredentials(credentials)
                await fetchUsageData()
            } catch {
                let isAuth = (error as? APIError)?.isAuthError ?? false
                uiState = .error(
                    message: error.localizedDescription,
                    isAuthError: isAuth
                )
            }
        }
    }

    func onManualLogin(sessionKey: String) {
        onLoginComplete(sessionKey: sessionKey)
    }

    func refresh() {
        Task {
            isRefreshing = true
            await fetchUsageData()
            isRefreshing = false
        }
    }

    func logout() {
        autoRefreshTask?.cancel()
        autoRefreshTask = nil
        api.cancelPendingRequests()
        keychain.clearCredentials()
        uiState = .loginRequired
        lastUpdated = nil
    }

    private func fetchUsageData() async {
        guard let credentials = keychain.getCredentials() else {
            uiState = .loginRequired
            return
        }

        do {
            let data = try await api.fetchUsageData(credentials: credentials)
            uiState = .success(data)
            lastUpdated = formatLastUpdated()
            startAutoRefresh()
        } catch {
            let isAuth = (error as? APIError)?.isAuthError ?? false
            if isAuth {
                keychain.clearCredentials()
            }

            if case .success = uiState {
                // Keep existing data, just update timestamp note
                lastUpdated = "Update failed - \(formatLastUpdated())"
            } else {
                uiState = .error(
                    message: error.localizedDescription,
                    isAuthError: isAuth
                )
            }
        }
    }

    private func startAutoRefresh() {
        autoRefreshTask?.cancel()
        autoRefreshTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(Self.updateIntervalSeconds * 1_000_000_000))
                if Task.isCancelled { break }
                await fetchUsageData()
            }
        }
    }

    private func formatLastUpdated() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: Date())
    }

    deinit {
        autoRefreshTask?.cancel()
        api.cancelPendingRequests()
    }
}
