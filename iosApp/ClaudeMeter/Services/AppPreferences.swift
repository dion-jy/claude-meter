import Foundation

class AppPreferences: ObservableObject {
    static let shared = AppPreferences()

    private let defaults = UserDefaults.standard

    @Published var showSonnet: Bool {
        didSet { defaults.set(showSonnet, forKey: Keys.showSonnet) }
    }
    @Published var showOpus: Bool {
        didSet { defaults.set(showOpus, forKey: Keys.showOpus) }
    }
    @Published var showCowork: Bool {
        didSet { defaults.set(showCowork, forKey: Keys.showCowork) }
    }
    @Published var showOauthApps: Bool {
        didSet { defaults.set(showOauthApps, forKey: Keys.showOauthApps) }
    }
    @Published var showExtraUsage: Bool {
        didSet { defaults.set(showExtraUsage, forKey: Keys.showExtraUsage) }
    }
    @Published var coachEnabled: Bool {
        didSet { defaults.set(coachEnabled, forKey: Keys.coachEnabled) }
    }

    private init() {
        // Register defaults
        defaults.register(defaults: [
            Keys.showSonnet: true,
            Keys.showOpus: false,
            Keys.showCowork: false,
            Keys.showOauthApps: false,
            Keys.showExtraUsage: true,
            Keys.coachEnabled: true
        ])

        self.showSonnet = defaults.bool(forKey: Keys.showSonnet)
        self.showOpus = defaults.bool(forKey: Keys.showOpus)
        self.showCowork = defaults.bool(forKey: Keys.showCowork)
        self.showOauthApps = defaults.bool(forKey: Keys.showOauthApps)
        self.showExtraUsage = defaults.bool(forKey: Keys.showExtraUsage)
        self.coachEnabled = defaults.bool(forKey: Keys.coachEnabled)
    }

    var visibleMetrics: Set<String> {
        var set = Set<String>()
        if showSonnet { set.insert("sonnet") }
        if showOpus { set.insert("opus") }
        if showCowork { set.insert("cowork") }
        if showOauthApps { set.insert("oauth_apps") }
        if showExtraUsage { set.insert("extra_usage") }
        return set
    }

    private enum Keys {
        static let showSonnet = "show_sonnet"
        static let showOpus = "show_opus"
        static let showCowork = "show_cowork"
        static let showOauthApps = "show_oauth_apps"
        static let showExtraUsage = "show_extra_usage"
        static let coachEnabled = "coach_enabled"
    }
}
