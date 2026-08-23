import SwiftUI
import Charts

/// Q1 -> Q2 -> Q3 as narrowing lanes: each driver's gap to the segment's
/// best time, carried forward until elimination. The shape of the lines
/// tells the session story before a number is read.
struct QualifyingLadderView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = QualifyingLadderViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    /// Data-space x where the trailing label gutter begins. The x domain runs
    /// past Q3 to reserve this space inside the plot.
    private let labelAnchorX = 3.08

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { data in
            if data.isEmpty {
                EmptyStateView(icon: "stopwatch", title: "No Qualifying Data",
                               message: "No qualifying session times are available for this race.")
            } else {
                content(data)
            }
        }
        .navigationTitle("Qualifying Ladder")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task(id: "\(year)-\(round)") { await vm.load(year: year, round: round) }
    }

    private func content(_ data: [LadderDriver]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.md) {
                Text("Gap to session best · lower is slower · line ends at elimination")
                    .font(.caption).foregroundStyle(Theme.Palette.textSecondary)

                chart(data)

                Text(vm.focused == nil
                     ? "Tap a driver to trace their session."
                     : "Tap again to show the full field.")
                    .font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
                driverChips(data)
            }
            .padding(Theme.Space.md)
        }
    }

    private func chart(_ data: [LadderDriver]) -> some View {
        // Draw the focused driver last so their line sits on top of the field.
        let ordered = data.sorted { ($0.code == vm.focused ? 1 : 0) < ($1.code == vm.focused ? 1 : 0) }
        return Chart {
            ForEach(ordered) { driver in
                let lit = vm.isLit(driver.code)
                ForEach(driver.points) { point in
                    LineMark(
                        x: .value("Segment", Double(point.column)),
                        y: .value("Gap", point.value),
                        // Without an explicit series every driver's points fold
                        // into one polyline, which is what turned this chart
                        // into a single zig-zag across the whole field.
                        series: .value("Driver", driver.code)
                    )
                    .foregroundStyle(Color.team(driver.teamColor).opacity(lit ? 1 : Theme.Chart.mutedOpacity))
                    .lineStyle(.init(lineWidth: lit ? Theme.Chart.lineWidth : Theme.Chart.mutedLineWidth,
                                     lineCap: .round, lineJoin: .round))
                    .interpolationMethod(.monotone)
                }
                ForEach(driver.points) { point in
                    PointMark(x: .value("Segment", Double(point.column)), y: .value("Gap", point.value))
                        .foregroundStyle(Color.team(driver.teamColor).opacity(lit ? 1 : Theme.Chart.mutedOpacity))
                        .symbolSize(lit ? Theme.Chart.pointSize : Theme.Chart.pointSize * 0.5)
                }
                // The focused driver is labelled where their line actually
                // ends — including mid-chart for a Q1/Q2 elimination.
                if driver.code == vm.focused, let last = driver.points.last {
                    PointMark(x: .value("Segment", Double(last.column)), y: .value("Gap", last.value))
                        .foregroundStyle(.clear)
                        .annotation(position: .topTrailing, spacing: 4) {
                            Text("\(driver.code)\(driver.suffix)")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Color.team(driver.teamColor))
                                .padding(.horizontal, 5).padding(.vertical, 2)
                                .background(Theme.Palette.background.opacity(0.85), in: Capsule())
                        }
                }
            }
        }
        .chartLegend(.hidden)
        // Domain runs past Q3 to reserve an in-plot gutter for the labels.
        .chartXScale(domain: 0.75...3.95)
        .chartXAxis {
            AxisMarks(values: [1.0, 2.0, 3.0]) { value in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                AxisValueLabel {
                    if let v = value.as(Double.self) {
                        Text("Q\(Int(v))").font(.caption2.weight(.bold))
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks { value in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                AxisValueLabel {
                    if let v = value.as(Double.self) {
                        Text(v == 0 ? "best" : String(format: "+%.1fs", v)).font(.caption2)
                    }
                }
            }
        }
        .chartOverlay { proxy in
            ChartTrailingLabels(proxy: proxy, labels: vm.gutterLabels(data),
                                anchorX: labelAnchorX, width: 88)
        }
        .frame(height: Theme.Chart.height(400, typeSize))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Qualifying ladder chart")
        .accessibilityValue(data.map { "\($0.code)\($0.suffix)" }.joined(separator: ", "))
    }

    private func driverChips(_ data: [LadderDriver]) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: Theme.Space.sm)],
                  spacing: Theme.Space.sm) {
            ForEach(data) { driver in
                let on = vm.focused == driver.code
                Button { vm.focus(driver.code) } label: {
                    VStack(spacing: 1) {
                        Text(driver.code).font(.subheadline.weight(.bold))
                        Text(driver.outcomeLabel).font(.caption2)
                    }
                    .foregroundStyle(on ? .black : Theme.Palette.textPrimary)
                    .frame(maxWidth: .infinity, minHeight: Theme.minTouch)
                    .background(on ? Color.team(driver.teamColor) : Theme.Palette.surfaceElevated,
                                in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.sm)
                        .stroke(Color.team(driver.teamColor), lineWidth: on ? 0 : 1))
                }
                .accessibilityLabel("\(driver.code), \(driver.outcomeLabel)")
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
    }
}

// MARK: - View model

struct LadderPoint: Identifiable {
    let column: Int // 1 = Q1, 2 = Q2, 3 = Q3
    let value: Double // seconds behind that segment's best
    var id: Int { column }
}

struct LadderDriver: Identifiable {
    let code: String
    let teamColor: String?
    let points: [LadderPoint]
    let finalPosition: Int?
    let eliminatedAfter: Int? // 1 or 2, nil if reached/survived Q3

    var id: String { code }
    var suffix: String {
        outcomeLabel.isEmpty ? "" : " · \(outcomeLabel)"
    }
    var outcomeLabel: String {
        if let eliminatedAfter { return "out Q\(eliminatedAfter)" }
        if let finalPosition { return "P\(finalPosition)" }
        return ""
    }
    /// Reached Q3, so the line runs to the right edge and can carry a label
    /// in the trailing gutter.
    var reachedQ3: Bool { points.contains { $0.column == 3 } }
}

@MainActor
final class QualifyingLadderViewModel: ObservableObject {
    @Published var state: Loadable<[LadderDriver]> = .idle
    /// Driver code being traced, or nil for the whole field.
    @Published var focused: String?
    private var loadedKey: String?

    func focus(_ code: String) {
        Haptics.selection()
        focused = focused == code ? nil : code
    }

    /// True when a driver's line should be drawn at full weight.
    func isLit(_ code: String) -> Bool { focused == nil || focused == code }

    /// Labels for the right-hand gutter: the Q3 runners, whose lines all end
    /// at the same x. Eliminated drivers are labelled in place when focused,
    /// and listed in the chips below otherwise.
    func gutterLabels(_ data: [LadderDriver]) -> [ChartSeriesLabel] {
        data.filter { $0.reachedQ3 && $0.code != focused }.compactMap { driver in
            guard let last = driver.points.last else { return nil }
            let lit = isLit(driver.code)
            return ChartSeriesLabel(
                id: driver.code,
                text: "\(driver.code) \(driver.outcomeLabel)",
                color: Color.team(driver.teamColor).opacity(lit ? 1 : Theme.Chart.mutedOpacity),
                value: last.value
            )
        }
    }

    func load(year: Int, round: Int) async {
        let key = "\(year)-\(round)"
        if loadedKey == key, case .loaded = state { return }
        state = .loading
        do {
            let response = try await APIClient.shared.results(year: year, round: round, session: "Q")
            let rows = response.results

            let q1Times = rows.compactMap { Self.seconds(from: $0.q1) }
            let q2Times = rows.compactMap { Self.seconds(from: $0.q2) }
            let q3Times = rows.compactMap { Self.seconds(from: $0.q3) }
            let bestQ1 = q1Times.min()
            let bestQ2 = q2Times.min()
            let bestQ3 = q3Times.min()

            let drivers: [LadderDriver] = rows.compactMap { row in
                var points: [LadderPoint] = []
                if let t = Self.seconds(from: row.q1), let best = bestQ1 {
                    points.append(LadderPoint(column: 1, value: t - best))
                }
                if let t = Self.seconds(from: row.q2), let best = bestQ2 {
                    points.append(LadderPoint(column: 2, value: t - best))
                }
                if let t = Self.seconds(from: row.q3), let best = bestQ3 {
                    points.append(LadderPoint(column: 3, value: t - best))
                }
                guard !points.isEmpty, let code = row.abbreviation else { return nil }
                let eliminatedAfter: Int? = points.count < 3 ? points.last?.column : nil
                let finalPosition = points.count == 3 ? row.position.map { Int($0) } : nil
                return LadderDriver(code: code, teamColor: row.teamColor, points: points,
                                     finalPosition: finalPosition, eliminatedAfter: eliminatedAfter)
            }
            state = .loaded(drivers)
            loadedKey = key
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    /// Parses a "m:ss.mmm" lap-time string into seconds.
    private static func seconds(from text: String?) -> Double? {
        guard let text, !text.isEmpty else { return nil }
        let parts = text.split(separator: ":")
        if parts.count == 2, let minutes = Double(parts[0]), let seconds = Double(parts[1]) {
            return minutes * 60 + seconds
        }
        return Double(text)
    }
}
