import SwiftUI

struct ForecastView: View {
    let usageData: UsageData
    let usageHistory: [UsageHistoryEntry]
    let onBack: () -> Void

    @State private var dragOffset: CGFloat = 0

    var body: some View {
        let screenWidth = UIScreen.main.bounds.width

        ZStack {
            Color.darkBackground.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    // Graph card
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Weekly Usage Forecast")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.textPrimary)

                        ForecastGraph(
                            usageData: usageData,
                            usageHistory: usageHistory
                        )
                        .frame(height: 250)
                    }
                    .padding(16)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.darkCard)
                    )

                    // Stats card
                    ForecastStats(usageData: usageData, usageHistory: usageHistory)
                        .padding(16)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.darkCard)
                        )

                    // Legend
                    ForecastLegend(usageData: usageData, usageHistory: usageHistory)
                        .padding(16)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.darkCard)
                        )
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
        }
        .navigationTitle("Forecast")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.textSecondary)
                }
            }
        }
        .toolbarBackground(Color.darkBackground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .offset(x: dragOffset)
        .gesture(
            DragGesture()
                .onChanged { value in
                    if value.translation.width > 0 {
                        dragOffset = value.translation.width
                    }
                }
                .onEnded { value in
                    if value.translation.width > screenWidth * 0.3 {
                        withAnimation(.easeOut(duration: 0.2)) {
                            dragOffset = screenWidth
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                            onBack()
                        }
                    } else {
                        withAnimation(.easeOut(duration: 0.2)) {
                            dragOffset = 0
                        }
                    }
                }
        )
    }
}

// MARK: - Forecast Calculation

private struct ForecastCalc {
    let weeklyUtil: Double
    let elapsedDays: Double
    let remainingDays: Double
    let burningRatePerHour: Double
    let projectedTotal: Double
    let depletionHoursFromNow: Double?
    let weekStart: Date
    let weekEnd: Date

    init(usageData: UsageData) {
        let weekly = usageData.sevenDay
        weeklyUtil = weekly?.utilization ?? 0

        if let resetsAt = weekly?.resetsAt {
            weekEnd = resetsAt
            weekStart = resetsAt.addingTimeInterval(-7 * 24 * 3600)
            let totalSeconds = 7.0 * 24 * 3600
            let elapsed = Date().timeIntervalSince(weekStart)
            elapsedDays = max(elapsed / 86400, 0)
            remainingDays = max((totalSeconds - elapsed) / 86400, 0)
        } else {
            weekEnd = Date().addingTimeInterval(7 * 24 * 3600)
            weekStart = Date()
            elapsedDays = 0
            remainingDays = 7
        }

        let elapsedHours = elapsedDays * 24
        if elapsedHours > 0 {
            burningRatePerHour = weeklyUtil / elapsedHours
        } else {
            burningRatePerHour = 0
        }

        let remainingHours = remainingDays * 24
        projectedTotal = weeklyUtil + (burningRatePerHour * remainingHours)

        if burningRatePerHour > 0 && weeklyUtil < 100 {
            let hours = (100.0 - weeklyUtil) / burningRatePerHour
            depletionHoursFromNow = hours
        } else {
            depletionHoursFromNow = nil
        }
    }
}

// MARK: - Forecast Graph

private struct ForecastGraph: View {
    let usageData: UsageData
    let usageHistory: [UsageHistoryEntry]

    var body: some View {
        let calc = ForecastCalc(usageData: usageData)

        Canvas { context, size in
            let leftMargin: CGFloat = 40
            let bottomMargin: CGFloat = 30
            let graphWidth = size.width - leftMargin
            let graphHeight = size.height - bottomMargin

            // Grid lines and Y-axis labels
            for i in 0...4 {
                let pct = Double(i) * 25
                let y = graphHeight - (CGFloat(pct) / 100.0) * graphHeight
                var path = Path()
                path.move(to: CGPoint(x: leftMargin, y: y))
                path.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(path, with: .color(.white.opacity(0.1)), lineWidth: 0.5)

                let text = Text("\(Int(pct))%")
                    .font(.system(size: 9))
                    .foregroundColor(.white.opacity(0.4))
                context.draw(context.resolve(text), at: CGPoint(x: leftMargin - 8, y: y), anchor: .trailing)
            }

            // X-axis labels
            for day in 0...7 {
                let x = leftMargin + (CGFloat(day) / 7.0) * graphWidth
                let text = Text("D\(day)")
                    .font(.system(size: 9))
                    .foregroundColor(.white.opacity(0.4))
                context.draw(context.resolve(text), at: CGPoint(x: x, y: graphHeight + 14), anchor: .center)
            }

            // Danger zone (>80%)
            let dangerTop = graphHeight - (80.0 / 100.0) * graphHeight
            let dangerRect = CGRect(x: leftMargin, y: 0, width: graphWidth, height: dangerTop)
            context.fill(Path(dangerRect), with: .color(Color.orange.opacity(0.08)))

            // Historical data polyline
            if !usageHistory.isEmpty {
                let weekStart = calc.weekStart.timeIntervalSince1970
                let weekDuration = 7.0 * 24 * 3600

                var histPath = Path()
                var firstPoint = true
                for entry in usageHistory {
                    let dayFraction = (entry.timestamp - weekStart) / weekDuration
                    guard dayFraction >= 0 && dayFraction <= 1 else { continue }
                    let x = leftMargin + CGFloat(dayFraction) * graphWidth
                    let y = graphHeight - CGFloat(min(entry.utilization, 100) / 100.0) * graphHeight
                    if firstPoint {
                        histPath.move(to: CGPoint(x: x, y: y))
                        firstPoint = false
                    } else {
                        histPath.addLine(to: CGPoint(x: x, y: y))
                    }
                }
                context.stroke(histPath, with: .color(.claudePurple), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            }

            // Current position dot
            let nowDayFraction = calc.elapsedDays / 7.0
            let nowX = leftMargin + CGFloat(nowDayFraction) * graphWidth
            let nowY = graphHeight - CGFloat(min(calc.weeklyUtil, 100) / 100.0) * graphHeight

            let outerCircle = Path(ellipseIn: CGRect(x: nowX - 5, y: nowY - 5, width: 10, height: 10))
            context.fill(outerCircle, with: .color(.claudePurple))
            let innerCircle = Path(ellipseIn: CGRect(x: nowX - 3, y: nowY - 3, width: 6, height: 6))
            context.fill(innerCircle, with: .color(.white))

            // Projection dashed line
            if calc.burningRatePerHour > 0 {
                if let depletionHours = calc.depletionHoursFromNow {
                    let depletionDayFraction = nowDayFraction + (depletionHours / (7.0 * 24))
                    if depletionDayFraction <= 1.0 {
                        // Line to depletion point
                        let depX = leftMargin + CGFloat(depletionDayFraction) * graphWidth
                        let depY = graphHeight - graphHeight // y=0 = 100%
                        drawDashedLine(context: context, from: CGPoint(x: nowX, y: nowY), to: CGPoint(x: depX, y: depY), color: .claudePurple)

                        // Red depletion dot
                        let redDot = Path(ellipseIn: CGRect(x: depX - 4, y: depY - 4, width: 8, height: 8))
                        context.fill(redDot, with: .color(.red))

                        // Flat red line at 100% to end
                        let endX = leftMargin + graphWidth
                        drawDashedLine(context: context, from: CGPoint(x: depX, y: depY), to: CGPoint(x: endX, y: depY), color: .red)
                    } else {
                        // Projects to end of week without depletion
                        let endUtil = min(calc.projectedTotal, 100)
                        let endY = graphHeight - CGFloat(endUtil / 100.0) * graphHeight
                        let endX = leftMargin + graphWidth
                        drawDashedLine(context: context, from: CGPoint(x: nowX, y: nowY), to: CGPoint(x: endX, y: endY), color: .claudePurple)
                    }
                } else {
                    // Safe - project to end
                    let endUtil = min(calc.projectedTotal, 100)
                    let endY = graphHeight - CGFloat(endUtil / 100.0) * graphHeight
                    let endX = leftMargin + graphWidth
                    drawDashedLine(context: context, from: CGPoint(x: nowX, y: nowY), to: CGPoint(x: endX, y: endY), color: .claudePurple)
                }
            }
        }
    }

    private func drawDashedLine(context: GraphicsContext, from: CGPoint, to: CGPoint, color: Color) {
        var path = Path()
        path.move(to: from)
        path.addLine(to: to)
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
    }
}

// MARK: - Forecast Stats

private struct ForecastStats: View {
    let usageData: UsageData
    let usageHistory: [UsageHistoryEntry]

    var body: some View {
        let calc = ForecastCalc(usageData: usageData)

        HStack(spacing: 0) {
            StatColumn(
                title: "Burning Rate",
                value: String(format: "%.2f%%/h", calc.burningRatePerHour)
            )

            Divider()
                .frame(height: 40)
                .background(Color.white.opacity(0.1))

            StatColumn(
                title: "Day",
                value: String(format: "%.1f / 7", calc.elapsedDays)
            )

            Divider()
                .frame(height: 40)
                .background(Color.white.opacity(0.1))

            StatColumn(
                title: "Depletion",
                value: depletionString(calc)
            )
        }
    }

    private func depletionString(_ calc: ForecastCalc) -> String {
        guard let hours = calc.depletionHoursFromNow else { return "Safe" }
        if hours > 48 { return String(format: "%.1fd", hours / 24) }
        if hours > 1 { return String(format: "%.1fh", hours) }
        return String(format: "%.0fm", hours * 60)
    }
}

private struct StatColumn: View {
    let title: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 11))
                .foregroundColor(.textMuted)
            Text(value)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.textPrimary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Forecast Legend

private struct ForecastLegend: View {
    let usageData: UsageData
    let usageHistory: [UsageHistoryEntry]

    var body: some View {
        let calc = ForecastCalc(usageData: usageData)
        let willDeplete = calc.depletionHoursFromNow != nil
            && (calc.elapsedDays / 7.0 + (calc.depletionHoursFromNow! / (7.0 * 24))) <= 1.0

        VStack(alignment: .leading, spacing: 8) {
            Text("Legend")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.textSecondary)

            LegendRow(color: .claudePurple, dashed: false, label: "Actual usage")
            LegendRow(color: .claudePurple, dashed: true, label: "Projected usage")
            LegendRow(color: .orange.opacity(0.3), dashed: false, label: "Danger zone (>80%)", isRect: true)
            if willDeplete {
                LegendRow(color: .red, dashed: false, label: "Depletion point", isDot: true)
            }
        }
    }
}

private struct LegendRow: View {
    let color: Color
    var dashed: Bool = false
    let label: String
    var isRect: Bool = false
    var isDot: Bool = false

    var body: some View {
        HStack(spacing: 8) {
            if isDot {
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                    .frame(width: 20)
            } else if isRect {
                RoundedRectangle(cornerRadius: 2)
                    .fill(color)
                    .frame(width: 20, height: 10)
            } else {
                Canvas { context, size in
                    var path = Path()
                    path.move(to: CGPoint(x: 0, y: size.height / 2))
                    path.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                    if dashed {
                        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, dash: [4, 3]))
                    } else {
                        context.stroke(path, with: .color(color), lineWidth: 2.5)
                    }
                }
                .frame(width: 20, height: 10)
            }

            Text(label)
                .font(.system(size: 12))
                .foregroundColor(.textSecondary)
        }
    }
}
