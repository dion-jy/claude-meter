import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = UsageViewModel()
    @StateObject private var preferences = AppPreferences.shared
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
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
                onSettingsClick: { showSettings = true }
            )
            .sheet(isPresented: $showSettings) {
                SettingsView(preferences: preferences)
            }
        }
    }
}
