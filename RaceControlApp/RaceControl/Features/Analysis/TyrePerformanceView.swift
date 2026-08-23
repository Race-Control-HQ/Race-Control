import SwiftUI
import Charts

struct TyrePerformanceView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = TyrePerformanceViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round, force: true)
        } content: { data in
            if !data.available || data.stints.isEmpty {
                EmptyStateView(icon: "circle.dashed", title: "No Tyre Data",
                               message: "No clean stints are available for degradation analysis.")
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: Theme.Space.md) {
                        let stints = vm.visible(data.stints)
                        Chart {
                            // The scatter first, translucent and small. At 800+
                            // laps a race, opaque 35pt symbols merge into one
                            // solid blob; at this weight the pile-up itself
                            // reads as shading.
                            ForEach(stints) { stint in
                                ForEach(stint.points, id: \.lap) { point in
                                    PointMark(
                                        x: .value("Tyre life", point.tyreLife),
                                        y: .value("Delta", Double(point.deltaMs) / 1000)
                                    )
                                    .foregroundStyle(TyreCompound.color(stint.compound).opacity(0.30))
                                    .symbolSize(16)
                                }
                            }
                            // One median line per compound, on top. The
                            // per-stint regressions this replaces were 30-odd
                            // overlapping lines — a starburst when drawn in
                            // front, invisible when drawn behind. Either way
                            // they couldn't answer the question the chart is
                            // for: which compound falls away fastest.
                            ForEach(vm.medianTrend(data.stints)) { point in
                                LineMark(
                                    x: .value("Tyre life", point.tyreLife),
                                    y: .value("Median delta", point.delta),
                                    series: .value("Compound", point.compound)
                                )
                                .foregroundStyle(TyreCompound.color(point.compound))
                                .lineStyle(.init(lineWidth: 3, lineCap: .round, lineJoin: .round))
                                .interpolationMethod(.monotone)
                            }
                        }
                        .chartLegend(.hidden)
                        .chartYScale(domain: vm.deltaDomain(data.stints))
                        .chartXAxis {
                            AxisMarks { value in
                                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                                AxisValueLabel {
                                    if let lap = value.as(Double.self) {
                                        Text("\(Int(lap))").font(.caption2)
                                    }
                                }
                            }
                        }
                        .chartYAxis {
                            AxisMarks { value in
                                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                                AxisValueLabel {
                                    if let s = value.as(Double.self) {
                                        Text(String(format: "%+.1fs", s)).font(.caption2)
                                    }
                                }
                            }
                        }
                        .frame(height: Theme.Chart.height(340, typeSize))
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Tyre performance chart")

                        let clipped = vm.clippedCount(data.stints)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Tyre life (laps on the set) vs. lap-time delta. Each line is the median across every stint on that compound, in \(Int(TyrePerformanceViewModel.binLaps))-lap bins.")
                            if clipped > 0 {
                                Text("\(clipped) outlying \(clipped == 1 ? "lap is" : "laps are") beyond the scale.")
                            }
                        }
                        .font(.caption2).foregroundStyle(Theme.Palette.textTertiary)

                        Picker("Compound", selection: $vm.compound) {
                            Text("All").tag("ALL")
                            ForEach(vm.compounds(data.stints), id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.segmented)

                        ForEach(data.compoundBaselines) { baseline in
                            HStack {
                                Circle().fill(TyreCompound.color(baseline.compound))
                                    .frame(width: 10, height: 10)
                                Text(baseline.compound)
                                Spacer()
                                Text(String(format: "%+.3f s/lap median", baseline.slopeSecPerLap))
                                    .foregroundStyle(Theme.Palette.textSecondary)
                            }
                            .font(.footnote)
                        }

                        if data.compoundBaselines.contains(where: { $0.slopeSecPerLap < 0 }) {
                            Text("A negative median means lap times fell over the stint — the car getting lighter on fuel outweighing tyre wear.")
                                .font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
                        }
                    }
                    .padding(Theme.Space.md)
                }
            }
        }
        .navigationTitle("Tyre Degradation")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }
}

@MainActor
final class TyrePerformanceViewModel: ObservableObject {
    @Published var state: Loadable<TyrePerformanceResponse> = .idle
    @Published var compound = "ALL"

    func load(year: Int, round: Int, force: Bool = false) async {
        if !force, case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.tyrePerformance(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func compounds(_ stints: [TyrePerformanceStint]) -> [String] {
        Array(Set(stints.compactMap { $0.compound?.uppercased() })).sorted()
    }

    func visible(_ stints: [TyrePerformanceStint]) -> [TyrePerformanceStint] {
        compound == "ALL" ? stints : stints.filter { $0.compound?.uppercased() == compound }
    }

    /// Y range covering the bulk of the cloud.
    ///
    /// In-laps and damaged-car laps that survive the server's filter sit
    /// several seconds off the pace. Letting them set the domain crushes every
    /// real point into a thin band — and Charts then rounds the axis outward
    /// from there, so most of the plot ends up empty. Clip to the 1st–99th
    /// percentile instead; `clippedCount` reports what fell outside.
    func deltaDomain(_ stints: [TyrePerformanceStint]) -> ClosedRange<Double> {
        let deltas = visible(stints)
            .flatMap { $0.points.map { Double($0.deltaMs) / 1000 } }
            .sorted()
        guard deltas.count > 2 else { return -1...3 }
        let lo = percentile(deltas, 0.01)
        let hi = percentile(deltas, 0.99)
        guard hi > lo else { return (lo - 1)...(hi + 1) }
        let pad = (hi - lo) * 0.12
        return (lo - pad)...(hi + pad)
    }

    /// Laps falling outside the plotted window, so the caption can own up to
    /// them rather than silently dropping data.
    func clippedCount(_ stints: [TyrePerformanceStint]) -> Int {
        let range = deltaDomain(stints)
        return visible(stints).reduce(0) { total, stint in
            total + stint.points.count {
                let value = Double($0.deltaMs) / 1000
                return value < range.lowerBound || value > range.upperBound
            }
        }
    }

    /// Width of a tyre-life bucket, in laps.
    static let binLaps: Double = 3
    /// Laps a bucket needs before its median is worth plotting.
    static let minBinSamples = 5

    /// Median lap-time delta per compound, in fixed tyre-life bins.
    ///
    /// Medians rather than means so one in-lap can't drag a bucket. Bins are
    /// dropped below `minBinSamples`: at the long-stint end only a couple of
    /// cars are still out, and a median of two laps spikes the tail of the
    /// line in a way that reads as degradation but is just thin data.
    func medianTrend(_ stints: [TyrePerformanceStint]) -> [TyreTrendPoint] {
        var buckets: [String: [Int: [Double]]] = [:]
        for stint in visible(stints) {
            let compound = stint.compound?.uppercased() ?? "UNKNOWN"
            for point in stint.points where point.tyreLife >= 0 {
                let slot = Int(point.tyreLife / Self.binLaps)
                buckets[compound, default: [:]][slot, default: []].append(Double(point.deltaMs) / 1000)
            }
        }
        return buckets.flatMap { compound, slots -> [TyreTrendPoint] in
            slots.sorted { $0.key < $1.key }.compactMap { slot, values in
                guard values.count >= Self.minBinSamples else { return nil }
                return TyreTrendPoint(
                    compound: compound,
                    tyreLife: (Double(slot) + 0.5) * Self.binLaps,
                    delta: percentile(values.sorted(), 0.5)
                )
            }
        }
    }

    private func percentile(_ sorted: [Double], _ p: Double) -> Double {
        let index = Int((Double(sorted.count - 1) * p).rounded())
        return sorted[min(max(index, 0), sorted.count - 1)]
    }
}

/// One point on a compound's median degradation line.
struct TyreTrendPoint: Identifiable {
    let compound: String
    let tyreLife: Double
    let delta: Double
    var id: String { "\(compound)-\(tyreLife)" }
}
