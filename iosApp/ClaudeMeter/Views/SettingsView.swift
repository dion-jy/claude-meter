import SwiftUI

private let githubURL = "https://github.com/CUN-bjy/claude-meter"
private let privacyPolicyURL = "https://github.com/CUN-bjy/claude-meter/blob/main_app/PRIVACY_POLICY.md"
private let donateURL = "https://paypal.me/JunyeobBaek"
private let appVersion = "1.0.0"

struct SettingsView: View {
    @ObservedObject var preferences: AppPreferences
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        // Display section
                        SectionLabel(text: "Display")
                        VStack(spacing: 0) {
                            SettingsToggleRow(
                                title: "Sonnet (7d)",
                                isOn: $preferences.showSonnet
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title: "Opus (7d)",
                                isOn: $preferences.showOpus
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title: "Cowork (7d)",
                                isOn: $preferences.showCowork
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title: "OAuth Apps (7d)",
                                isOn: $preferences.showOauthApps
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title: "Extra Usage",
                                isOn: $preferences.showExtraUsage
                            )
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.darkCard)
                        )

                        // Notification section
                        SectionLabel(text: "Notification")
                        VStack(spacing: 0) {
                            SettingsToggleRow(
                                title: "Productivity Coach",
                                subtitle: "Smart usage tips & reset alerts",
                                isOn: $preferences.coachEnabled
                            )
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.darkCard)
                        )

                        // About section
                        SectionLabel(text: "About")
                        VStack(spacing: 0) {
                            SettingsInfoRow(title: "Version", value: appVersion)
                            SettingsDivider()
                            SettingsLinkRow(title: "GitHub") {
                                openURL(githubURL)
                            }
                            SettingsDivider()
                            SettingsLinkRow(title: "Privacy Policy") {
                                openURL(privacyPolicyURL)
                            }
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.darkCard)
                        )

                        // Buy Me a Coffee
                        Button(action: { openURL(donateURL) }) {
                            Text("\u{2615}  Buy me a coffee")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(Color(red: 1, green: 0.87, blue: 0))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }

                        // Disclaimer
                        Text("This app is not affiliated with or endorsed by Anthropic.")
                            .font(.system(size: 11))
                            .foregroundColor(.textMuted)
                            .padding(.horizontal, 4)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.left")
                            .foregroundColor(.textSecondary)
                    }
                }
            }
            .toolbarBackground(Color.darkBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private func openURL(_ urlString: String) {
        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Settings components

private struct SectionLabel: View {
    let text: String

    var body: some View {
        HStack {
            Text(text)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.textSecondary)
                .padding(.leading, 4)
            Spacer()
        }
    }
}

private struct SettingsDivider: View {
    var body: some View {
        Divider()
            .background(Color.darkBackground)
            .padding(.horizontal, 16)
    }
}

private struct SettingsToggleRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.textPrimary)
                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundColor(.textMuted)
                }
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .tint(.claudePurple)
                .labelsHidden()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

private struct SettingsInfoRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.textPrimary)
            Spacer()
            Text(value)
                .font(.system(size: 14))
                .foregroundColor(.textMuted)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

private struct SettingsLinkRow: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.textPrimary)
                Spacer()
                Text("\u{203A}")
                    .font(.system(size: 18))
                    .foregroundColor(.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
    }
}
