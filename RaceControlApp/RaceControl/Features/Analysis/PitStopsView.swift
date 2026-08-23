import SwiftUI
import Charts

struct PitStopsView: View {
    let year: Int
    let round: Int
    let title: String
    @StateObject private var vm = PitStopsViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round, force: true)
        } content: { data in
            if !data.available || data.stops.isEmpty {
                EmptyStateView(icon: "wrench.and.screwdriver", title: "No Pit-Stop Data",
                               message: "No timed pit-lane transits are available.")
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: Theme.Space.md) {
                        Chart {
                            ForEach(data.stops) { stop in
                                BarMark(
                                    x: .value("Pit-lane time", Double(stop.lossMs) / 1000),
                                    y: .value("Stop", "\(stop.driverCode) L\(stop.lap)")
                                )
                                .foregroundStyle(Color.team(stop.teamColor))
                            }
                            if let median = data.circuitMedianLossMs {
                                RuleMark(x: .value("Circuit median", Double(median) / 1000))
                                    .foregroundStyle(Theme.Palette.textSecondary)
                                    .lineStyle(.init(dash: [5, 4]))
                                    .annotation(position: .top) { Text("Median").font(.caption2) }
                            }
                        }
                        .chartLegend(.hidden)
                        .chartXAxis {
                            AxisMarks { value in
                                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                                AxisValueLabel {
                                    if let s = value.as(Double.self) {
                                        Text(String(format: "%.0fs", s)).font(.caption2)
                                    }
                                }
                            }
                        }
                        .chartYAxis {
                            AxisMarks { _ in AxisValueLabel().font(.caption2.weight(.semibold)) }
                        }
                        .frame(height: max(300, CGFloat(data.stops.count) * Theme.Chart.rowHeight(typeSize)))
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Pit stop chart")

                        ForEach(data.stops) { stop in
                            HStack {
                                Text(stop.driverCode).font(.headline)
                                    .foregroundStyle(Color.team(stop.teamColor))
                                Text("L\(stop.lap) · \(String(format: "%.3fs", Double(stop.lossMs) / 1000))")
                                Spacer()
                                Text("P\(stop.entryPosition.map(String.init) ?? "–") → P\(stop.rejoinPosition.map(String.init) ?? "–")")
                                Text(stop.outcome.capitalized)
                                    .font(.caption.bold())
                            }
                            .padding(Theme.Space.sm)
                            .background(Theme.Palette.surfaceElevated,
                                        in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                        }
                    }
                    .padding(Theme.Space.md)
                }
            }
        }
        .navigationTitle("Pit Stops")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }
}

@MainActor
final class PitStopsViewModel: ObservableObject {
    @Published var state: Loadable<PitStopsResponse> = .idle

    func load(year: Int, round: Int, force: Bool = false) async {
        if !force, case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.pitStops(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
