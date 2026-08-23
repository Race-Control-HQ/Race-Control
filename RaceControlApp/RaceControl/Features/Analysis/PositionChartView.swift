import SwiftUI
import Charts

struct PositionChartView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = PositionChartViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round, force: true)
        } content: { data in
            if data.replay.frames.isEmpty {
                EmptyStateView(icon: "arrow.up.arrow.down", title: "No Position Data",
                               message: "Lap-by-lap positions aren't available.")
            } else {
                let points = vm.visiblePoints(in: data.replay)
                let lastLap = data.replay.frames.map(\.lap).max() ?? 1
                // Room at the right edge for the driver-code labels.
                let headroom = max(2, lastLap / 9)
                VStack(spacing: Theme.Space.sm) {
                    Chart {
                        PositionFlagMarks(periods: data.flags)
                        PositionLineMarks(points: points)
                        PositionPitMarks(markers: data.pits.filter { vm.selected.contains($0.code) })
                        PositionRetirementMarks(
                            markers: data.retirements.filter { vm.selected.contains($0.code) }
                        )
                    }
                    .chartYScale(domain: Double(-max(20, data.replay.drivers.count))...(-1))
                    .chartXScale(domain: 1...Double(lastLap + headroom))
                    .chartYAxis {
                        // One label per position is ~17pt apart on a phone;
                        // step it so the axis stays legible.
                        AxisMarks(values: vm.positionAxisValues(data.replay.drivers.count)) { value in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                            AxisValueLabel {
                                if let position = value.as(Double.self) {
                                    Text("P\(Int(abs(position)))").font(.caption2)
                                }
                            }
                        }
                    }
                    .chartXAxis {
                        AxisMarks(values: vm.lapAxisValues(lastLap)) { value in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                            AxisValueLabel {
                                if let lap = value.as(Double.self) {
                                    Text("\(Int(lap))").font(.caption2)
                                }
                            }
                        }
                    }
                    .chartLegend(.hidden)
                    .chartOverlay { proxy in
                        ChartTrailingLabels(proxy: proxy, labels: vm.endLabels(points),
                                            anchorX: Double(lastLap) + 0.4, width: 40)
                    }
                    .frame(height: Theme.Chart.height(350, typeSize))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Position chart")
                    .padding(Theme.Space.md)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(data.replay.drivers) { driver in
                                let on = vm.selected.contains(driver.code)
                                Button { vm.toggle(driver.code) } label: {
                                    Text(driver.code).font(.caption.bold())
                                        .foregroundStyle(on ? .black : Theme.Palette.textPrimary)
                                        .padding(.horizontal, 10).frame(minHeight: Theme.minTouch)
                                        .background(on ? Color.team(driver.teamColor) : Theme.Palette.surfaceElevated,
                                                    in: Capsule())
                                }
                            }
                        }
                        .padding(.horizontal, Theme.Space.md)
                    }
                    Text("White dots are pit stops; red crosses are retirements.")
                        .font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                }
            }
        }
        .navigationTitle("Position Chart")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }
}

// Every mark plots lap and position as `Double` so the chart's scales stay a
// single type — `ChartProxy.position(for:)`, which the trailing labels rely
// on, can't resolve a value whose type doesn't match its axis.
private struct PositionFlagMarks: ChartContent {
    let periods: [FlagPeriod]

    var body: some ChartContent {
        ForEach(periods) { period in
            RectangleMark(
                xStart: .value("Start", Double(period.startLap)),
                xEnd: .value("End", Double(period.endLap + 1))
            )
            .foregroundStyle(FlagStyle.color(period.type).opacity(0.15))
        }
    }
}

private struct PositionLineMarks: ChartContent {
    let points: [PositionPoint]

    var body: some ChartContent {
        ForEach(points) { point in
            LineMark(
                x: .value("Lap", Double(point.lap)),
                y: .value("Position", point.plottedPosition),
                series: .value("Driver", point.code)
            )
            .foregroundStyle(Color.team(point.teamColor))
            .lineStyle(.init(lineWidth: Theme.Chart.lineWidth, lineCap: .round, lineJoin: .round))
            .interpolationMethod(.catmullRom)
        }
    }
}

private struct PositionPitMarks: ChartContent {
    let markers: [PositionMarker]

    var body: some ChartContent {
        ForEach(markers) { marker in
            PointMark(
                x: .value("Lap", Double(marker.lap)),
                y: .value("Position", marker.plottedPosition)
            )
            .symbol(.circle)
            .symbolSize(45)
            .foregroundStyle(.white)
        }
    }
}

private struct PositionRetirementMarks: ChartContent {
    let markers: [PositionMarker]

    var body: some ChartContent {
        ForEach(markers) { marker in
            PointMark(
                x: .value("Lap", Double(marker.lap)),
                y: .value("Position", marker.plottedPosition)
            )
            .symbol {
                Image(systemName: "xmark")
                    .font(.caption.bold())
            }
            .symbolSize(70)
            .foregroundStyle(Theme.Palette.negative)
        }
    }
}

struct PositionChartData {
    let replay: RaceReplay
    let flags: [FlagPeriod]
    let pits: [PositionMarker]
    let retirements: [PositionMarker]
}

struct PositionPoint: Identifiable {
    let code: String
    let teamColor: String?
    let lap: Int
    let position: Int
    var id: String { "\(code)-\(lap)" }
    var plottedPosition: Double { Double(-position) }
}

struct PositionMarker: Identifiable {
    let code: String
    let lap: Int
    let position: Int
    let kind: String
    var id: String { "\(kind)-\(code)-\(lap)" }
    var plottedPosition: Double { Double(-position) }
}

@MainActor
final class PositionChartViewModel: ObservableObject {
    @Published var state: Loadable<PositionChartData> = .idle
    @Published var selected: Set<String> = []

    func load(year: Int, round: Int, force: Bool = false) async {
        if !force, case .loaded = state { return }
        state = .loading
        do {
            async let replayTask = APIClient.shared.replay(year: year, round: round)
            async let strategyTask = APIClient.shared.strategy(year: year, round: round)
            async let flagsTask = APIClient.shared.flags(year: year, round: round)
            let (replay, strategy, flags) = try await (replayTask, strategyTask, flagsTask)
            if selected.isEmpty { selected = Set(replay.drivers.prefix(5).map(\.code)) }
            let pits = strategy.drivers.flatMap { driver in
                driver.stints.dropFirst().compactMap { stint in
                    marker(code: driver.code, lap: stint.startLap, kind: "pit", replay: replay)
                }
            }
            let retirements = strategy.drivers.filter(\.retired).compactMap { driver in
                let lastLap = replay.frames.reversed().first {
                    $0.order.contains(where: { $0.driver == driver.code })
                }?.lap
                return lastLap.flatMap {
                    marker(code: driver.code, lap: $0, kind: "retirement", replay: replay)
                }
            }
            state = .loaded(.init(replay: replay, flags: flags.periods,
                                  pits: pits, retirements: retirements))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func visiblePoints(in replay: RaceReplay) -> [PositionPoint] {
        replay.drivers.filter { selected.contains($0.code) }.flatMap { driver in
            replay.frames.compactMap { frame in
                frame.order.first(where: { $0.driver == driver.code }).map {
                    PositionPoint(code: driver.code, teamColor: driver.teamColor,
                                  lap: frame.lap, position: $0.position)
                }
            }
        }
    }

    func toggle(_ code: String) {
        if selected.contains(code) { selected.remove(code) } else { selected.insert(code) }
    }

    /// Position ticks stepped so the axis stays readable on a phone: always
    /// P1 and the back of the grid, with a handful in between.
    func positionAxisValues(_ driverCount: Int) -> [Double] {
        Theme.Chart.ticks(through: max(20, driverCount)).map { Double(-$0) }
    }

    /// Lap ticks, capped at the last racing lap so the label gutter doesn't
    /// grow ticks for laps that were never run.
    func lapAxisValues(_ lastLap: Int) -> [Double] {
        Theme.Chart.ticks(through: lastLap).map(Double.init)
    }

    /// Driver code at the end of each visible line, for the trailing gutter.
    func endLabels(_ points: [PositionPoint]) -> [ChartSeriesLabel] {
        Dictionary(grouping: points, by: \.code).compactMap { code, pts in
            guard let last = pts.max(by: { $0.lap < $1.lap }) else { return nil }
            return ChartSeriesLabel(id: code, text: code,
                                    color: Color.team(last.teamColor),
                                    value: last.plottedPosition)
        }
    }

    private func marker(code: String, lap: Int, kind: String, replay: RaceReplay) -> PositionMarker? {
        guard let position = replay.frames.first(where: { $0.lap == lap })?
            .order.first(where: { $0.driver == code })?.position else { return nil }
        return .init(code: code, lap: lap, position: position, kind: kind)
    }
}
