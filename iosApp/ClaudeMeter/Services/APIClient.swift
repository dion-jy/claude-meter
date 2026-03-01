import Foundation

// MARK: - Models

enum StatusLevel {
    case normal, warning, critical
}

struct UsageMetric {
    let utilization: Double
    let resetsAt: Date?

    var remainingDuration: TimeInterval? {
        guard let resetsAt = resetsAt else { return nil }
        let remaining = resetsAt.timeIntervalSinceNow
        return remaining > 0 ? remaining : 0
    }

    var isExpired: Bool {
        guard let resetsAt = resetsAt else { return false }
        return Date() > resetsAt
    }

    var statusLevel: StatusLevel {
        switch utilization {
        case 90...: return .critical
        case 75...: return .warning
        default: return .normal
        }
    }
}

struct ExtraUsageInfo {
    var usedCents: Int?
    var limitCents: Int?
    var balanceCents: Int?
}

struct UsageData {
    let fiveHour: UsageMetric?
    let sevenDay: UsageMetric?
    let sevenDaySonnet: UsageMetric?
    let sevenDayOpus: UsageMetric?
    let sevenDayCowork: UsageMetric?
    let sevenDayOauthApps: UsageMetric?
    var extraUsage: UsageMetric?
    var extraUsageInfo: ExtraUsageInfo?
    let fetchedAt: Date
    let rawKeys: [String]

    var extraMetrics: [(String, UsageMetric)] {
        let defaultMetric = UsageMetric(utilization: 0, resetsAt: nil)
        return [
            ("Sonnet (7d)", sevenDaySonnet ?? defaultMetric),
            ("Opus (7d)", sevenDayOpus ?? defaultMetric),
            ("Cowork (7d)", sevenDayCowork ?? defaultMetric),
            ("OAuth Apps (7d)", sevenDayOauthApps ?? defaultMetric),
            ("Extra Usage", extraUsage ?? defaultMetric)
        ]
    }
}

// MARK: - Errors

enum APIError: LocalizedError {
    case authError(String)
    case cloudflare(String)
    case networkError(String)

    var errorDescription: String? {
        switch self {
        case .authError(let msg): return msg
        case .cloudflare(let msg): return msg
        case .networkError(let msg): return msg
        }
    }

    var isAuthError: Bool {
        if case .authError = self { return true }
        return false
    }
}

// MARK: - API Client

class APIClient {
    static let shared = APIClient()

    private let baseURL = "https://claude.ai"
    private let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 30
        self.session = URLSession(configuration: config)
    }

    func fetchUsageData(credentials: Credentials) async throws -> UsageData {
        // Parallel fetch: usage, overage, prepaid
        async let usageResult = fetchJSON(
            url: "\(baseURL)/api/organizations/\(credentials.organizationId)/usage",
            sessionKey: credentials.sessionKey
        )
        async let overageResult = try? fetchJSON(
            url: "\(baseURL)/api/organizations/\(credentials.organizationId)/overage_spend_limit",
            sessionKey: credentials.sessionKey
        )
        async let prepaidResult = try? fetchJSON(
            url: "\(baseURL)/api/organizations/\(credentials.organizationId)/prepaid/credits",
            sessionKey: credentials.sessionKey
        )

        let usageJSON = try await usageResult
        let overageJSON = await overageResult
        let prepaidJSON = await prepaidResult

        var usageData = parseUsageData(json: usageJSON)

        // Merge overage spending data
        if let overageJSON = overageJSON {
            let limit = overageJSON["monthly_credit_limit"] as? Int
                ?? overageJSON["spend_limit_amount_cents"] as? Int
            let used = overageJSON["used_credits"] as? Int
                ?? overageJSON["balance_cents"] as? Int
            let enabled: Bool
            if let isEnabled = overageJSON["is_enabled"] as? Bool {
                enabled = isEnabled
            } else {
                enabled = limit != nil
            }

            if enabled, let limit = limit, limit > 0, let used = used {
                let utilization = (Double(used) / Double(limit)) * 100
                usageData.extraUsage = UsageMetric(utilization: utilization, resetsAt: nil)
                usageData.extraUsageInfo = ExtraUsageInfo(usedCents: used, limitCents: limit)
            }
        }

        // Merge prepaid balance
        if let prepaidJSON = prepaidJSON, let amount = prepaidJSON["amount"] as? Int {
            var info = usageData.extraUsageInfo ?? ExtraUsageInfo()
            info.balanceCents = amount
            usageData.extraUsageInfo = info
        }

        return usageData
    }

    func fetchOrganizationId(sessionKey: String) async throws -> String {
        let json = try await fetchJSON(
            url: "\(baseURL)/api/organizations",
            sessionKey: sessionKey,
            expectArray: true
        )

        guard let orgs = json["_array"] as? [[String: Any]],
              let first = orgs.first,
              let uuid = first["uuid"] as? String else {
            throw APIError.networkError("No organizations found.")
        }
        return uuid
    }

    func cancelPendingRequests() {
        session.getAllTasks { tasks in
            tasks.forEach { $0.cancel() }
        }
    }

    func validateSession(credentials: Credentials) async -> Bool {
        do {
            _ = try await fetchUsageData(credentials: credentials)
            return true
        } catch {
            return false
        }
    }

    // MARK: - Private

    private func fetchJSON(url: String, sessionKey: String, expectArray: Bool = false) async throws -> [String: Any] {
        guard let url = URL(string: url) else {
            throw APIError.networkError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.setValue("sessionKey=\(sessionKey)", forHTTPHeaderField: "Cookie")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(baseURL, forHTTPHeaderField: "Referer")

        let (data, response) = try await session.data(for: request)
        let httpResponse = response as? HTTPURLResponse
        let statusCode = httpResponse?.statusCode ?? 0
        let body = String(data: data, encoding: .utf8) ?? ""

        if statusCode == 401 || statusCode == 403 {
            throw APIError.authError("Session expired. Please log in again.")
        }

        if statusCode < 200 || statusCode >= 300 {
            throw APIError.networkError("HTTP \(statusCode)")
        }

        if body.contains("Just a moment") || body.contains("Enable JavaScript") {
            throw APIError.cloudflare("Cloudflare challenge detected. Please try again.")
        }

        if body.trimmingCharacters(in: .whitespaces).hasPrefix("<") {
            throw APIError.networkError("Unexpected HTML response from server.")
        }

        let jsonObj = try JSONSerialization.jsonObject(with: data)

        if expectArray, let array = jsonObj as? [[String: Any]] {
            return ["_array": array]
        }

        guard let dict = jsonObj as? [String: Any] else {
            throw APIError.networkError("Invalid JSON response")
        }
        return dict
    }

    private func parseUsageMetric(json: [String: Any]?) -> UsageMetric? {
        guard let json = json else { return nil }
        let utilization = json["utilization"] as? Double ?? 0.0
        var resetsAt: Date?
        if let resetsAtString = json["resets_at"] as? String, !resetsAtString.isEmpty {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            resetsAt = formatter.date(from: resetsAtString)
            if resetsAt == nil {
                formatter.formatOptions = [.withInternetDateTime]
                resetsAt = formatter.date(from: resetsAtString)
            }
        }
        return UsageMetric(utilization: utilization, resetsAt: resetsAt)
    }

    private func parseUsageData(json: [String: Any]) -> UsageData {
        return UsageData(
            fiveHour: parseUsageMetric(json: json["five_hour"] as? [String: Any]),
            sevenDay: parseUsageMetric(json: json["seven_day"] as? [String: Any]),
            sevenDaySonnet: parseUsageMetric(json: json["seven_day_sonnet"] as? [String: Any]),
            sevenDayOpus: parseUsageMetric(json: json["seven_day_opus"] as? [String: Any]),
            sevenDayCowork: parseUsageMetric(json: json["seven_day_cowork"] as? [String: Any]),
            sevenDayOauthApps: parseUsageMetric(json: json["seven_day_oauth_apps"] as? [String: Any]),
            extraUsage: parseUsageMetric(json: json["extra_usage"] as? [String: Any]),
            extraUsageInfo: nil,
            fetchedAt: Date(),
            rawKeys: Array(json.keys)
        )
    }
}

// MARK: - Format Utilities

func formatDuration(_ interval: TimeInterval) -> String {
    if interval <= 0 { return "Resetting..." }

    let totalSeconds = Int(interval)
    let days = totalSeconds / 86400
    let hours = (totalSeconds % 86400) / 3600
    let minutes = (totalSeconds % 3600) / 60

    if days > 0 { return "Resets in \(days)d \(hours)h" }
    if hours > 0 { return "Resets in \(hours)h \(minutes)m" }
    if minutes > 0 { return "Resets in \(minutes)m" }
    return "Resetting soon..."
}
