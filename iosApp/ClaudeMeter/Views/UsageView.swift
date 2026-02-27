import SwiftUI

struct UsageView: View {
    let uiState: UiState
    let isRefreshing: Bool
    let lastUpdated: String?
    let visibleMetrics: Set<String>
    let onRefresh: () -> Void
    let onLogout: () -> Void
    let onLoginClick: () -> Void
    let onManualLogin: (String) -> Void
    let onSettingsClick: () -> Void
    var onForecastClick: (() -> Void)? = nil

    @State private var showLogin = false

    var body: some View {
        ZStack {
            Color.darkBackground.ignoresSafeArea()

            switch uiState {
            case .loading:
                LoadingContent()
            case .loginRequired:
                LoginContent(
                    onLoginClick: { showLogin = true },
                    onManualLogin: onManualLogin
                )
            case .success(let data):
                UsageContent(
                    data: data,
                    lastUpdated: lastUpdated,
                    visibleMetrics: visibleMetrics
                )
            case .error(let message, let isAuthError):
                ErrorContent(
                    message: message,
                    isAuthError: isAuthError,
                    onRetry: isAuthError ? { showLogin = true } : onRefresh
                )
            }
        }
        .navigationTitle("Claude Meter")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if case .success = uiState {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    if let onForecastClick = onForecastClick {
                        Button(action: onForecastClick) {
                            Image(systemName: "chart.line.uptrend.xyaxis")
                                .foregroundColor(.textSecondary)
                        }
                    }
                    Button(action: onSettingsClick) {
                        Image(systemName: "gearshape")
                            .foregroundColor(.textSecondary)
                    }
                    Button(action: onRefresh) {
                        if isRefreshing {
                            ProgressView()
                                .tint(.claudePurpleLight)
                                .scaleEffect(0.8)
                        } else {
                            Image(systemName: "arrow.clockwise")
                                .foregroundColor(.textSecondary)
                        }
                    }
                    .disabled(isRefreshing)
                    Button(action: onLogout) {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                            .foregroundColor(.textSecondary)
                    }
                }
            }
        }
        .toolbarBackground(Color.darkBackground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .fullScreenCover(isPresented: $showLogin) {
            LoginView(
                onSessionCaptured: { sessionKey in
                    showLogin = false
                    onManualLogin(sessionKey)
                },
                onClose: { showLogin = false }
            )
        }
    }
}

// MARK: - Loading Content

private struct LoadingContent: View {
    var body: some View {
        ZStack {
            Color.darkBackground.ignoresSafeArea()

            Canvas { context, size in
                let minDim = min(size.width, size.height)
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let outerRadius = minDim * 0.45
                let sw = minDim * 0.13
                let midRadius = outerRadius - sw / 2
                let rect = CGRect(
                    x: center.x - midRadius,
                    y: center.y - midRadius,
                    width: midRadius * 2,
                    height: midRadius * 2
                )

                // Purple arc
                var purplePath = Path()
                purplePath.addArc(
                    center: center, radius: midRadius,
                    startAngle: .degrees(290), endAngle: .degrees(200),
                    clockwise: false
                )
                context.stroke(purplePath, with: .color(.claudePurple), lineWidth: sw)

                // White arc
                var whitePath = Path()
                whitePath.addArc(
                    center: center, radius: midRadius,
                    startAngle: .degrees(200), endAngle: .degrees(290),
                    clockwise: false
                )
                context.stroke(whitePath, with: .color(.white), lineWidth: sw)
            }
            .frame(width: 180, height: 180)
        }
    }
}

// MARK: - Login Content

private struct LoginContent: View {
    let onLoginClick: () -> Void
    let onManualLogin: (String) -> Void

    @State private var showManualInput = false
    @State private var sessionKeyInput = ""

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            // Ring logo
            Canvas { context, size in
                let s = min(size.width, size.height)
                let center = CGPoint(x: s / 2, y: s / 2)
                let outerR = s * 0.45
                let sw = s * 0.13
                let midR = outerR - sw / 2

                var purplePath = Path()
                purplePath.addArc(center: center, radius: midR, startAngle: .degrees(290), endAngle: .degrees(200), clockwise: false)
                context.stroke(purplePath, with: .color(.claudePurple), lineWidth: sw)

                var whitePath = Path()
                whitePath.addArc(center: center, radius: midR, startAngle: .degrees(200), endAngle: .degrees(290), clockwise: false)
                context.stroke(whitePath, with: .color(.white), lineWidth: sw)
            }
            .frame(width: 80, height: 80)

            Spacer().frame(height: 24)

            Text("Claude Meter")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.textPrimary)

            Spacer().frame(height: 8)

            Text("Sign in to monitor your Claude usage")
                .font(.system(size: 14))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)

            Spacer().frame(height: 32)

            Button(action: onLoginClick) {
                Text("Sign in with Claude.ai")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Color.claudePurple)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Spacer().frame(height: 16)

            Button(action: { showManualInput.toggle() }) {
                Text("Enter session key manually")
                    .font(.system(size: 14))
                    .foregroundColor(.textSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.textMuted, lineWidth: 1)
                    )
            }

            if showManualInput {
                VStack(spacing: 12) {
                    TextField("sk-ant-...", text: $sessionKeyInput)
                        .textFieldStyle(.plain)
                        .padding(14)
                        .background(Color.darkSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.darkSurfaceVariant, lineWidth: 1)
                        )
                        .foregroundColor(.textPrimary)
                        .autocapitalization(.none)
                        .autocorrectionDisabled()

                    Button(action: {
                        let trimmed = sessionKeyInput.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty {
                            onManualLogin(trimmed)
                        }
                    }) {
                        Text("Connect")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(sessionKeyInput.isEmpty ? Color.claudePurpleDark.opacity(0.5) : Color.claudePurpleDark)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(sessionKeyInput.isEmpty)
                }
                .padding(.top, 16)
                .transition(.opacity.combined(with: .move(edge: .top)))
                .animation(.easeInOut(duration: 0.3), value: showManualInput)
            }

            Spacer()
        }
        .padding(24)
    }
}

// MARK: - Usage Content

private struct UsageContent: View {
    let data: UsageData
    let lastUpdated: String?
    let visibleMetrics: Set<String>

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                let defaultMetric = UsageMetric(utilization: 0, resetsAt: nil)

                UsageCard(
                    title: "Current Session",
                    subtitle: "5-hour window",
                    metric: data.fiveHour ?? defaultMetric,
                    totalWindowHours: 5.0
                )

                UsageCard(
                    title: "Weekly Limit",
                    subtitle: "7-day window",
                    metric: data.sevenDay ?? defaultMetric,
                    totalWindowHours: 168.0
                )

                // Extra metrics (filtered by settings)
                let filteredMetrics = data.extraMetrics.filter { (label, _) in
                    if label.contains("Sonnet") { return visibleMetrics.contains("sonnet") }
                    if label.contains("Opus") { return visibleMetrics.contains("opus") }
                    if label.contains("Cowork") { return visibleMetrics.contains("cowork") }
                    if label.contains("OAuth") { return visibleMetrics.contains("oauth_apps") }
                    if label.contains("Extra") { return visibleMetrics.contains("extra_usage") }
                    return true
                }

                ForEach(Array(filteredMetrics.enumerated()), id: \.offset) { _, item in
                    let (label, metric) = item
                    if label == "Extra Usage" {
                        MiniUsageCard(
                            label: label,
                            metric: metric,
                            extraUsageInfo: data.extraUsageInfo
                        )
                    } else {
                        MiniUsageCard(label: label, metric: metric)
                    }
                }

                if let lastUpdated = lastUpdated {
                    Text("Last updated at \(lastUpdated)")
                        .font(.system(size: 11))
                        .foregroundColor(.textMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
        }
    }
}

// MARK: - Usage Card

private struct UsageCard: View {
    let title: String
    let subtitle: String
    let metric: UsageMetric
    var totalWindowHours: Double = 5.0

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.textPrimary)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.textMuted)
            }

            UsageProgressBar(
                label: "",
                utilization: metric.utilization,
                statusLevel: metric.statusLevel,
                remainingDuration: metric.remainingDuration,
                totalWindowHours: totalWindowHours
            )
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.darkCard)
        )
    }
}

// MARK: - Mini Usage Card

private struct MiniUsageCard: View {
    let label: String
    let metric: UsageMetric
    var totalWindowHours: Double = 168.0
    var extraUsageInfo: ExtraUsageInfo? = nil

    var body: some View {
        Group {
            if let info = extraUsageInfo {
                ExtraUsageBar(
                    label: label,
                    utilization: metric.utilization,
                    info: info
                )
            } else {
                UsageProgressBar(
                    label: label,
                    utilization: metric.utilization,
                    statusLevel: metric.statusLevel,
                    remainingDuration: metric.remainingDuration,
                    totalWindowHours: totalWindowHours
                )
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.darkCard.opacity(0.7))
        )
    }
}

// MARK: - Extra Usage Bar

private struct ExtraUsageBar: View {
    let label: String
    let utilization: Double
    let info: ExtraUsageInfo

    @State private var animatedProgress: CGFloat = 0

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.textSecondary)
                Spacer()

                HStack(spacing: 8) {
                    if let balance = info.balanceCents {
                        Text("Bal $\(balance / 100)")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.statusExtraLight)
                    }
                    if let used = info.usedCents, let limit = info.limitCents {
                        Text("$\(used / 100)/$\(limit / 100)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.statusExtra)
                    } else {
                        Text(String(format: "%.1f%%", utilization))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.statusExtra)
                    }
                }
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 5)
                        .fill(Color.progressTrack)

                    if animatedProgress > 0 {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(LinearGradient(
                                colors: [.statusExtra, .statusExtraLight],
                                startPoint: .leading,
                                endPoint: .trailing
                            ))
                            .frame(width: geometry.size.width * animatedProgress)
                    }
                }
            }
            .frame(height: 10)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8)) {
                animatedProgress = CGFloat(min(max(utilization / 100.0, 0), 1))
            }
        }
    }
}

// MARK: - Error Content

private struct ErrorContent: View {
    let message: String
    let isAuthError: Bool
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer()

            Text(isAuthError ? "Session Expired" : "Error")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.statusCritical)

            Text(message)
                .font(.system(size: 14))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)

            Spacer().frame(height: 12)

            Button(action: onRetry) {
                Text(isAuthError ? "Sign In Again" : "Retry")
                    .font(.system(size: 15))
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.claudePurple)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Spacer()
        }
        .padding(24)
    }
}
