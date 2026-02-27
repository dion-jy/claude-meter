import SwiftUI

enum Screen {
    case usage
    case forecast
}

struct ContentView: View {
    @StateObject private var viewModel = UsageViewModel()
    @StateObject private var preferences = AppPreferences.shared
    @State private var showSettings = false
    @State private var currentScreen: Screen = .usage

    var body: some View {
        NavigationStack {
            switch currentScreen {
            case .usage:
                UsageView(
                    uiState: viewModel.uiState,
                    isRefreshing: viewModel.isRefreshing,
                    lastUpdated: viewModel.lastUpdated,
                    visibleMetrics: preferences.visibleMetrics,
                    onRefresh: viewModel.refresh,
                    onLogout: viewModel.logout,
                    onLoginClick: {},
                    onManualLogin: { sessionKey in
                        viewModel.onManualLogin(sessionKey: sessionKey)
                    },
                    onSettingsClick: { showSettings = true },
                    onForecastClick: {
                        if case .success = viewModel.uiState {
                            currentScreen = .forecast
                        }
                    }
                )
                .sheet(isPresented: $showSettings) {
                    SettingsView(preferences: preferences)
                }

            case .forecast:
                if case .success(let data) = viewModel.uiState {
                    ForecastView(
                        usageData: data,
                        usageHistory: viewModel.usageHistory,
                        onBack: { currentScreen = .usage }
                    )
                }
            }
        }
    }
}
