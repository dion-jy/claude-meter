import Foundation

struct UsageHistoryEntry: Codable {
    let timestamp: TimeInterval
    let utilization: Double

    enum CodingKeys: String, CodingKey {
        case timestamp = "t"
        case utilization = "u"
    }
}

class UsageHistoryStore {
    static let shared = UsageHistoryStore()

    private let defaults = UserDefaults.standard
    private let historyKey = "usage_history"
    private let maxEntries = 2016 // ~7 days at 5-min intervals

    func getHistory() -> [UsageHistoryEntry] {
        guard let data = defaults.data(forKey: historyKey) else { return [] }
        return (try? JSONDecoder().decode([UsageHistoryEntry].self, from: data)) ?? []
    }

    func addEntry(timestamp: TimeInterval, utilization: Double) {
        var entries = getHistory()
        entries.append(UsageHistoryEntry(timestamp: timestamp, utilization: utilization))
        if entries.count > maxEntries {
            entries = Array(entries.suffix(maxEntries))
        }
        saveHistory(entries)
    }

    func clearHistory() {
        defaults.removeObject(forKey: historyKey)
    }

    private func saveHistory(_ entries: [UsageHistoryEntry]) {
        if let data = try? JSONEncoder().encode(entries) {
            defaults.set(data, forKey: historyKey)
        }
    }
}
