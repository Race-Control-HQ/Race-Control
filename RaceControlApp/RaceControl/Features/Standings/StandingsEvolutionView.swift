import SwiftUI
import Charts

/// Championship progression: cumulative points per driver, round by round.
struct StandingsEvolutionView: View {
    let year: Int
    @StateObject private var vm = StandingsEvolutionViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year)
        } content: { data in
            if data.drivers.isEmpty {
                EmptyStateView(icon: "chart.line.uptrend.xyaxis", title: "No Data",
                               message: "Championship data isn't available for \(String(year)).")
            } else {
                content(data)
            }
        }
        .background(Theme.Palette.background)
        .task(id: year) { await vm.load(year: year) }
    }

    private func content(_ data: StandingsEvolution) -> some View {
        VStack(spacing: Theme.Space.sm) {
            chart(data).padding(Theme.Space.md)
            HStack {
                Text("Cumulative points by round").font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Button("Top 5") { vm.selectTop(data.drivers, 5) }.font(.footnote.weight(.semibold))
                Button("None") { vm.selected.removeAll() }.font(.footnote.weight(.semibold))
            }
            .padding(.horizontal, Theme.Space.md)
            driverChips(data.drivers)
        }
    }

    private func chart(_ data: StandingsEvolution) -> some View {
        let drivers = data.drivers.filter { vm.selected.contains($0.driverId) }
        let lastRound = drivers.compactMap { $0.series.last?.round }.max() ?? 1
        // Extend the x domain just enough to park the driver codes clear of
        // the final data point instead of clipping them at the plot edge.
        let headroom = max(2, lastRound / 9)
        return Chart {
            ForEach(drivers) { driver in
                ForEach(driver.series, id: \.round) { pt in
                    LineMark(x: .value("Round", Double(pt.round)), y: .value("Points", pt.points))
                        .foregroundStyle(Color.team(driver.teamColor))
                        .foregroundStyle(by: .value("Driver", driver.code ?? driver.driverId))
                        .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                         lineCap: .round, lineJoin: .round))
                        .interpolationMethod(.monotone)
                }
                if let last = driver.series.last {
                    PointMark(x: .value("Round", Double(last.round)), y: .value("Points", last.points))
                        .foregroundStyle(Color.team(driver.teamColor))
                        .symbolSize(Theme.Chart.pointSize)
                }
            }
        }
        .chartForegroundStyleScale(domain: drivers.map { $0.code ?? $0.driverId },
                                   range: drivers.map { Color.team($0.teamColor) })
        .chartLegend(.hidden)
        .chartXScale(domain: 1...Double(lastRound + headroom))
        .chartXAxis {
            // Explicit ticks, stopping at the last round raced — the gutter
            // beyond it exists for labels, not for rounds.
            AxisMarks(values: vm.roundAxisValues(lastRound)) { value in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                AxisValueLabel {
                    if let v = value.as(Double.self) { Text("\(Int(v))").font(.caption2) }
                }
            }
        }
        .chartYAxis {
            AxisMarks { _ in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
            }
        }
        .chartOverlay { proxy in
            ChartTrailingLabels(
                proxy: proxy,
                labels: drivers.compactMap { driver in
                    guard let last = driver.series.last else { return nil }
                    return ChartSeriesLabel(id: driver.driverId, text: driver.code ?? "",
                                            color: Color.team(driver.teamColor),
                                            value: last.points)
                },
                anchorX: Double(lastRound) + 0.35,
                width: 40
            )
        }
        .frame(height: Theme.Chart.height(300, typeSize))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Championship progression chart")
        .accessibilityValue(drivers.isEmpty
            ? "No drivers selected"
            : drivers.map { "\($0.name) \(Int($0.points)) points" }.joined(separator: ", "))
        .overlay {
            if drivers.isEmpty {
                Text("Select drivers below to plot their championship.")
                    .font(.subheadline).foregroundStyle(Theme.Palette.textSecondary)
                    .multilineTextAlignment(.center).padding()
            }
        }
    }

    private func driverChips(_ drivers: [EvolutionDriver]) -> some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 76), spacing: Theme.Space.sm)],
                      spacing: Theme.Space.sm) {
                ForEach(drivers) { d in
                    let on = vm.selected.contains(d.driverId)
                    Button { vm.toggle(d.driverId) } label: {
                        VStack(spacing: 1) {
                            Text(d.code ?? String(d.name.prefix(3)).uppercased())
                                .font(.subheadline.weight(.bold))
                            Text("\(Int(d.points))")
                                .font(.caption2).monospacedDigit()
                        }
                        .foregroundStyle(on ? .black : Theme.Palette.textPrimary)
                        .frame(maxWidth: .infinity, minHeight: Theme.minTouch)
                        .background(on ? Color.team(d.teamColor) : Theme.Palette.surfaceElevated,
                                    in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.sm)
                            .stroke(Color.team(d.teamColor), lineWidth: on ? 0 : 1))
                    }
                    .accessibilityLabel("\(d.name), \(Int(d.points)) points")
                    .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(Theme.Space.md)
        }
    }
}

@MainActor
final class StandingsEvolutionViewModel: ObservableObject {
    @Published var state: Loadable<StandingsEvolution> = .idle
    @Published var selected: Set<String> = []
    private var loadedYear: Int?

    func load(year: Int) async {
        if loadedYear == year, case .loaded = state { return }
        state = .loading
        do {
            let data = try await APIClient.shared.standingsEvolution(year: year)
            selectTop(data.drivers, 5)
            state = .loaded(data)
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func toggle(_ id: String) {
        Haptics.selection()
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }
    func selectTop(_ drivers: [EvolutionDriver], _ n: Int) {
        selected = Set(drivers.prefix(n).map(\.driverId))
    }

    /// Round ticks, thinned to about five so a 24-race season doesn't print a
    /// label every 14 points of width.
    func roundAxisValues(_ lastRound: Int) -> [Double] {
        Theme.Chart.ticks(through: lastRound).map(Double.init)
    }
}
