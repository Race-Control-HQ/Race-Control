import SwiftUI
import Charts

struct RaceTraceView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = RaceTraceViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round, force: true)
        } content: { data in
            if !data.available || data.drivers.isEmpty {
                EmptyStateView(icon: "chart.xyaxis.line", title: "No Race Trace",
                               message: "Cumulative race timing isn't available.")
            } else {
                VStack(spacing: Theme.Space.sm) {
                    Picker("Baseline", selection: $vm.mode) {
                        Text("Field median").tag("median")
                        Text("Leader").tag("leader")
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, Theme.Space.md)
                    .onChange(of: vm.mode) {
                        Task { await vm.load(year: year, round: round, force: true) }
                    }

                    Chart {
                        ForEach(data.periods) { period in
                            RectangleMark(
                                xStart: .value("Start", period.startLap),
                                xEnd: .value("End", period.endLap + 1)
                            )
                            .foregroundStyle(FlagStyle.color(period.type).opacity(0.15))
                        }
                        RuleMark(y: .value("Baseline", 0))
                            .lineStyle(.init(dash: [4, 4]))
                            .foregroundStyle(Theme.Palette.textTertiary)
                        ForEach(data.drivers.filter { vm.selected.contains($0.code) }) { driver in
                            ForEach(driver.laps, id: \.lap) { lap in
                                LineMark(
                                    x: .value("Lap", lap.lap),
                                    y: .value("Delta", Double(lap.deltaMs) / 1000),
                                    // Each driver needs its own series, or the
                                    // marks collapse into one polyline that
                                    // jumps between drivers lap by lap.
                                    series: .value("Driver", driver.code)
                                )
                                .foregroundStyle(Color.team(driver.teamColor))
                                .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                                 lineCap: .round, lineJoin: .round))
                                .interpolationMethod(.catmullRom)
                            }
                        }
                    }
                    .chartYScale(domain: vm.domain(data))
                    .chartLegend(.hidden)
                    .chartXAxis {
                        AxisMarks { _ in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                            AxisValueLabel().font(.caption2)
                        }
                    }
                    .chartYAxis {
                        AxisMarks { value in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                            AxisValueLabel {
                                if let v = value.as(Double.self) {
                                    // Tenths once the window is tight enough
                                    // that whole seconds would repeat.
                                    Text(vm.spanSeconds(data) < 12
                                         ? String(format: "%+.1fs", v)
                                         : String(format: "%+.0fs", v))
                                        .font(.caption2)
                                }
                            }
                        }
                    }
                    .frame(height: Theme.Chart.height(330, typeSize))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Race trace chart")
                    .padding(.horizontal, Theme.Space.md)

                    driverChips(data.drivers)
                    Text("Vertical distance between two lines is their real gap. Higher is further ahead.")
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .padding(.horizontal, Theme.Space.md)
                }
            }
        }
        .navigationTitle("Race Trace")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }

    private func driverChips(_ drivers: [RaceTraceDriver]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Theme.Space.sm) {
                ForEach(drivers) { driver in
                    let on = vm.selected.contains(driver.code)
                    Button {
                        vm.toggle(driver.code)
                    } label: {
                        Text(driver.code + (driver.retired ? " ·DNF" : ""))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(on ? .black : Theme.Palette.textPrimary)
                            .padding(.horizontal, 10)
                            .frame(minHeight: Theme.minTouch)
                            .background(on ? Color.team(driver.teamColor) : Theme.Palette.surfaceElevated,
                                        in: Capsule())
                    }
                }
            }
            .padding(.horizontal, Theme.Space.md)
        }
    }
}

@MainActor
final class RaceTraceViewModel: ObservableObject {
    @Published var state: Loadable<RaceTraceResponse> = .idle
    @Published var selected: Set<String> = []
    @Published var mode = "median"

    func load(year: Int, round: Int, force: Bool = false) async {
        if !force, case .loaded = state { return }
        state = .loading
        do {
            let data = try await APIClient.shared.raceTrace(year: year, round: round, mode: mode)
            if selected.isEmpty { selected = Set(data.drivers.prefix(5).map(\.code)) }
            state = .loaded(data)
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func toggle(_ code: String) {
        if selected.contains(code) { selected.remove(code) } else { selected.insert(code) }
    }

    /// Height of the visible y window, in seconds.
    func spanSeconds(_ data: RaceTraceResponse) -> Double {
        let range = domain(data)
        return range.upperBound - range.lowerBound
    }

    /// Y range for the drivers actually on screen.
    ///
    /// The server's `yDomainMs` covers the entire field, so one lapped or
    /// retired car stretches it to hundreds of seconds and squashes the
    /// selected drivers into a flat smear at the top of the plot. Scale to the
    /// selection instead, falling back to the server domain when nothing is
    /// selected or the values are degenerate.
    func domain(_ data: RaceTraceResponse) -> ClosedRange<Double> {
        let deltas = data.drivers
            .filter { selected.contains($0.code) }
            .flatMap { $0.laps.map { Double($0.deltaMs) / 1000 } }
        if let lo = deltas.min(), let hi = deltas.max(), hi > lo {
            let pad = max((hi - lo) * 0.08, 0.5)
            return (lo - pad)...(hi + pad)
        }
        guard let values = data.yDomainMs, values.count == 2,
              values[0] < values[1] else { return -1...1 }
        return Double(values[0]) / 1000...Double(values[1]) / 1000
    }
}
