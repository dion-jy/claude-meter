import Foundation
import Combine
import UserNotifications

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
    @Published var usageHistory: [UsageHistoryEntry] = []

    private let keychain = KeychainManager.shared
    private let api = APIClient.shared
    private let preferences = AppPreferences.shared
    private let historyStore = UsageHistoryStore.shared
    private var autoRefreshTask: Task<Void, Never>?

    private var prevSessionUtil: Double?
    private var prevWeeklyUtil: Double?
    private var prevSonnetUtil: Double?
    private var prevOpusUtil: Double?
    private var lastCoachEvalTime: TimeInterval = 0

    private static let updateIntervalSeconds: TimeInterval = 5 * 60
    private static let coachEvalIntervalSeconds: TimeInterval = 2 * 60 * 60

    init() {
        usageHistory = historyStore.getHistory()
        requestNotificationPermission()
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

            // Coach features
            if preferences.coachEnabled {
                detectResets(data)
                let now = Date().timeIntervalSince1970
                if now - lastCoachEvalTime >= Self.coachEvalIntervalSeconds {
                    evaluateCoachNotification(data)
                    lastCoachEvalTime = now
                }
            }

            recordUsageHistory(data)

            prevSessionUtil = data.fiveHour?.utilization
            prevWeeklyUtil = data.sevenDay?.utilization
            prevSonnetUtil = data.sevenDaySonnet?.utilization
            prevOpusUtil = data.sevenDayOpus?.utilization
        } catch {
            let isAuth = (error as? APIError)?.isAuthError ?? false
            if isAuth {
                keychain.clearCredentials()
            }

            if case .success = uiState {
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

    // MARK: - Coach Logic

    private func detectResets(_ data: UsageData) {
        let currentSessionUtil = data.fiveHour?.utilization ?? 0
        if let prev = prevSessionUtil, prev > 30, currentSessionUtil < 5 {
            sendLocalNotification(
                title: "Fully charged!",
                body: "Session reset complete — start a new session now"
            )
        }

        let currentWeeklyUtil = data.sevenDay?.utilization ?? 0
        if let prev = prevWeeklyUtil, prev > 30, currentWeeklyUtil < 5 {
            sendLocalNotification(
                title: "Weekly reset!",
                body: "Fresh weekly capacity — let's make this week count"
            )
            historyStore.clearHistory()
            usageHistory = []
        }

        let currentSonnetUtil = data.sevenDaySonnet?.utilization ?? 0
        if let prev = prevSonnetUtil, prev > 30, currentSonnetUtil < 5 {
            sendLocalNotification(
                title: "Sonnet reset!",
                body: "Sonnet weekly limit refreshed"
            )
        }

        let currentOpusUtil = data.sevenDayOpus?.utilization ?? 0
        if let prev = prevOpusUtil, prev > 30, currentOpusUtil < 5 {
            sendLocalNotification(
                title: "Opus reset!",
                body: "Opus weekly limit refreshed"
            )
        }
    }

    private func evaluateCoachNotification(_ data: UsageData) {
        let sessionUtil = data.fiveHour?.utilization ?? 0
        let sessionRemaining = data.fiveHour?.remainingDuration
        let weeklyUtil = data.sevenDay?.utilization ?? 0
        let weeklyRemaining = data.sevenDay?.remainingDuration
        let sonnetUtil = data.sevenDaySonnet?.utilization ?? 0
        let opusUtil = data.sevenDayOpus?.utilization ?? 0

        let title: String
        let message: String

        switch true {
        case sessionUtil >= 100:
            let timeStr = sessionRemaining.map { formatCoachDuration($0) } ?? ""
            title = "Session maxed out"
            message = "Resets in \(timeStr) — switch tasks or take a break!"

        case sessionUtil > 80 && (sessionRemaining ?? .infinity) < 30 * 60:
            title = "Reset imminent"
            message = "Take a break, come back fully charged"

        case sessionUtil < 30 && sessionUtil > 0 && (sessionRemaining ?? .infinity) < 3600:
            let timeStr = sessionRemaining.map { formatCoachDuration($0) } ?? ""
            title = "Session resets in \(timeStr)"
            message = "Don't waste the remaining capacity!"

        case sonnetUtil > 80:
            title = "Sonnet at \(Int(sonnetUtil))%"
            message = "Consider switching to other models"

        case opusUtil > 80:
            title = "Opus at \(Int(opusUtil))%"
            message = "Consider switching to other models"

        case weeklyUtil > 70 && (weeklyRemaining ?? 0) > 2 * 24 * 3600:
            title = "Weekly at \(Int(weeklyUtil))%"
            message = "Focus on high-impact tasks only"

        case weeklyUtil < 40 && weeklyUtil > 0 && (weeklyRemaining ?? .infinity) < 24 * 3600:
            title = "Last sprint!"
            message = "Use your remaining weekly capacity before reset"

        case weeklyUtil < 50 && (weeklyRemaining ?? 0) > 3 * 24 * 3600:
            title = "Plenty of capacity"
            message = "Perfect time for deep work!"

        default:
            return
        }

        sendLocalNotification(title: title, body: message)
    }

    private func recordUsageHistory(_ data: UsageData) {
        guard let weekly = data.sevenDay else { return }
        historyStore.addEntry(timestamp: Date().timeIntervalSince1970, utilization: weekly.utilization)
        usageHistory = historyStore.getHistory()
    }

    private func formatCoachDuration(_ interval: TimeInterval) -> String {
        if interval <= 0 { return "0m" }
        let totalSeconds = Int(interval)
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        if h > 0 { return "\(h)h \(m)m" }
        return "\(m)m"
    }

    // MARK: - Notifications

    private func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    private func sendLocalNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "coach-\(title.hashValue)-\(Int(Date().timeIntervalSince1970))",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    deinit {
        autoRefreshTask?.cancel()
        api.cancelPendingRequests()
    }
}
