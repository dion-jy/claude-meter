import SwiftUI

struct UsageProgressBar: View {
    let label: String
    let utilization: Double
    let statusLevel: StatusLevel
    let remainingDuration: TimeInterval?
    var totalWindowHours: Double = 5.0

    @State private var animatedProgress: CGFloat = 0
    @State private var animatedElapsed: CGFloat = 0

    private var barColor: Color {
        switch statusLevel {
        case .normal: return .statusNormal
        case .warning: return .statusWarning
        case .critical: return .statusCritical
        }
    }

    private var gradientColors: [Color] {
        switch statusLevel {
        case .normal:
            return [.claudePurpleDark, .claudePurple, .claudePurpleLight]
        case .warning:
            return [Color(red: 0xCC/255, green: 0x7A/255, blue: 0x20/255), .statusWarning, Color(red: 0xF0/255, green: 0xB0/255, blue: 0x60/255)]
        case .critical:
            return [Color(red: 0xCC/255, green: 0x30/255, blue: 0x30/255), .statusCritical, Color(red: 0xF0/255, green: 0x70/255, blue: 0x70/255)]
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                if !label.isEmpty {
                    Text(label)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.textSecondary)
                }
                Spacer()

                HStack(spacing: 8) {
                    if let remaining = remainingDuration {
                        CircularTimer(
                            remainingDuration: remaining,
                            color: barColor
                        )
                    }
                    Text(String(format: "%.1f%%", utilization))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(barColor)
                }
            }

            // Progress bar
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    // Track
                    RoundedRectangle(cornerRadius: 5)
                        .fill(Color.progressTrack)

                    // Elapsed time portion
                    if animatedElapsed > 0 {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(Color.claudePurple.opacity(0.25))
                            .frame(width: geometry.size.width * animatedElapsed)
                    }

                    // Usage portion
                    if animatedProgress > 0 {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(LinearGradient(
                                colors: gradientColors,
                                startPoint: .leading,
                                endPoint: .trailing
                            ))
                            .frame(width: geometry.size.width * animatedProgress)
                    }
                }
            }
            .frame(height: 10)

            if let remaining = remainingDuration {
                Text(formatDuration(remaining))
                    .font(.system(size: 11))
                    .foregroundColor(.textMuted)
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8)) {
                animatedProgress = CGFloat(min(max(utilization / 100.0, 0), 1))

                if let remaining = remainingDuration, totalWindowHours > 0 {
                    let totalWindowSeconds = totalWindowHours * 3600
                    let elapsed = 1.0 - remaining / totalWindowSeconds
                    animatedElapsed = CGFloat(min(max(elapsed, 0), 1))
                }
            }
        }
        .onChange(of: utilization) { newValue in
            withAnimation(.easeInOut(duration: 0.8)) {
                animatedProgress = CGFloat(min(max(newValue / 100.0, 0), 1))
            }
        }
    }
}

struct CircularTimer: View {
    let remainingDuration: TimeInterval
    let color: Color

    var body: some View {
        let totalSeconds = Float(remainingDuration)
        let progress = totalSeconds > 0 ? (totalSeconds.truncatingRemainder(dividingBy: 3600)) / 3600 : 0

        ZStack {
            Circle()
                .stroke(Color.progressTrack, lineWidth: 2)

            Circle()
                .trim(from: 0, to: CGFloat(progress))
                .stroke(color, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: 18, height: 18)
    }
}
