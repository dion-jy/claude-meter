import WidgetKit
import SwiftUI

// MARK: - Timeline Provider

struct UsageEntry: TimelineEntry {
    let date: Date
    let fiveHourUtilization: Double?
    let sevenDayUtilization: Double?
    let isLoggedIn: Bool
}

struct UsageTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> UsageEntry {
        UsageEntry(date: Date(), fiveHourUtilization: 42.5, sevenDayUtilization: 68.3, isLoggedIn: true)
    }

    func getSnapshot(in context: Context, completion: @escaping (UsageEntry) -> Void) {
        let entry = UsageEntry(date: Date(), fiveHourUtilization: 42.5, sevenDayUtilization: 68.3, isLoggedIn: true)
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<UsageEntry>) -> Void) {
        Task {
            let keychain = KeychainManager.shared
            guard let credentials = keychain.getCredentials() else {
                let entry = UsageEntry(date: Date(), fiveHourUtilization: nil, sevenDayUtilization: nil, isLoggedIn: false)
                let timeline = Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(30 * 60)))
                completion(timeline)
                return
            }

            do {
                let data = try await APIClient.shared.fetchUsageData(credentials: credentials)
                let entry = UsageEntry(
                    date: Date(),
                    fiveHourUtilization: data.fiveHour?.utilization,
                    sevenDayUtilization: data.sevenDay?.utilization,
                    isLoggedIn: true
                )
                let nextUpdate = Date().addingTimeInterval(30 * 60) // 30 minutes
                let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
                completion(timeline)
            } catch {
                let entry = UsageEntry(date: Date(), fiveHourUtilization: nil, sevenDayUtilization: nil, isLoggedIn: true)
                let nextUpdate = Date().addingTimeInterval(15 * 60) // Retry in 15 minutes
                let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
                completion(timeline)
            }
        }
    }
}

// MARK: - Widget View

struct ClaudeMeterWidgetEntryView: View {
    var entry: UsageEntry

    var body: some View {
        if !entry.isLoggedIn || (entry.fiveHourUtilization == nil && entry.sevenDayUtilization == nil) {
            NoDataView(isLoggedIn: entry.isLoggedIn)
        } else {
            UsageDataView(
                fiveHour: entry.fiveHourUtilization ?? 0,
                sevenDay: entry.sevenDayUtilization ?? 0
            )
        }
    }
}

private struct NoDataView: View {
    let isLoggedIn: Bool

    var body: some View {
        VStack(spacing: 4) {
            Text("Claude Meter")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Color(red: 0xE8/255, green: 0xE6/255, blue: 0xF0/255))
            Text(isLoggedIn ? "Loading..." : "Tap to sign in")
                .font(.system(size: 12))
                .foregroundColor(Color(red: 0xA0/255, green: 0x9B/255, blue: 0xB0/255))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 0x0D/255, green: 0x0D/255, blue: 0x0D/255))
    }
}

private struct UsageDataView: View {
    let fiveHour: Double
    let sevenDay: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Claude Meter")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Color(red: 0xE8/255, green: 0xE6/255, blue: 0xF0/255))

            WidgetUsageRow(label: "Session", utilization: fiveHour)
            WidgetProgressBar(utilization: fiveHour)

            WidgetUsageRow(label: "Weekly", utilization: sevenDay)
            WidgetProgressBar(utilization: sevenDay)
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(red: 0x0D/255, green: 0x0D/255, blue: 0x0D/255))
    }
}

private struct WidgetUsageRow: View {
    let label: String
    let utilization: Double

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(Color(red: 0xA0/255, green: 0x9B/255, blue: 0xB0/255))
            Spacer()
            Text(String(format: "%.1f%%", utilization))
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(statusColor(utilization))
        }
    }
}

private struct WidgetProgressBar: View {
    let utilization: Double

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color(red: 0x2A/255, green: 0x2A/255, blue: 0x40/255))

                let width = geometry.size.width * CGFloat(min(max(utilization / 100.0, 0), 1))
                if width > 0 {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(statusColor(utilization))
                        .frame(width: width)
                }
            }
        }
        .frame(height: 6)
    }
}

private func statusColor(_ utilization: Double) -> Color {
    switch utilization {
    case 90...: return Color(red: 0xE8/255, green: 0x54/255, blue: 0x54/255)
    case 75...: return Color(red: 0xE8/255, green: 0x94/255, blue: 0x3A/255)
    default: return Color(red: 0x6B/255, green: 0x4F/255, blue: 0xBB/255)
    }
}

// MARK: - Widget Configuration

struct ClaudeMeterWidget: Widget {
    let kind: String = "ClaudeMeterWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: UsageTimelineProvider()) { entry in
            ClaudeMeterWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Claude Meter")
        .description("Don't Waste a Single Token. Monitor your Claude.ai usage in real-time.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Widget Bundle

@main
struct ClaudeMeterWidgetBundle: WidgetBundle {
    var body: some Widget {
        ClaudeMeterWidget()
    }
}
