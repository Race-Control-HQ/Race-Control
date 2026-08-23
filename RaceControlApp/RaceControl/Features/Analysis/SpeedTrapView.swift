import SwiftUI
import Charts

/// Shared-scale dot plot of the four speed-trap detection points (I1, I2,
/// FL, ST), one row per driver. Exposes setup tradeoffs — low-drag top
/// speed vs. cornering compromise — that lap time and sector gaps alone
/// hide, and shows tow effects at a glance.
struct SpeedTrapView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = SpeedTrapViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { data in
            if data.isEmpty {
                EmptyStateView(icon: "speedometer", title: "No Speed-Trap Data",
                               message: "No qualifying speed-trap readings are available for this session.")
            } else {
                content(data)
            }
        }
        .navigationTitle("Speed Trap")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task(id: "\(year)-\(round)") { await vm.load(year: year, round: round) }
    }

    private func content(_ data: [SpeedTrapDriver]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.md) {
                Text("Speed at each detection point · km/h · fastest through the trap first")
                    .font(.caption).foregroundStyle(Theme.Palette.textSecondary)

                legend

                Chart {
                    ForEach(data) { driver in
                        ForEach(driver.points) { point in
                            LineMark(x: .value("Speed", point.speed), y: .value("Driver", driver.code),
                                     // One connector per driver row; without a
                                     // series they chain into a single line
                                     // stitching every row together.
                                     series: .value("Driver", driver.code))
                                .foregroundStyle(Color.team(driver.teamColor).opacity(0.6))
                                .lineStyle(.init(lineWidth: 2, lineCap: .round))
                        }
                        ForEach(driver.points) { point in
                            PointMark(x: .value("Speed", point.speed), y: .value("Driver", driver.code))
                                .foregroundStyle(Color.team(driver.teamColor))
                                .symbolSize(point.label == "ST" ? 90 : 40)
                        }
                    }
                }
                .chartLegend(.hidden)
                // Charts pads a numeric axis down to zero, which pushed every
                // reading into the right third of the plot and threw away the
                // width that makes the spread between drivers legible.
                .chartXScale(domain: vm.speedDomain(data))
                // The scale sits at the top as well as the foot: this chart is
                // one row per driver, so on a phone the bottom axis is 700pt
                // below the fold and effectively invisible.
                .chartXAxis {
                    AxisMarks(position: .top) { _ in
                        AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                        AxisValueLabel().font(.caption2)
                    }
                    AxisMarks(position: .bottom) { _ in
                        AxisValueLabel().font(.caption2)
                    }
                }
                .chartYAxis {
                    AxisMarks { _ in
                        // Without `centered` the code sits on the band edge,
                        // half a row above the dots it belongs to.
                        AxisValueLabel(centered: true).font(.caption2.weight(.bold))
                    }
                }
                // Pin the row order to the view model's sort instead of
                // letting Charts infer it. A categorical y domain renders
                // first-element-at-top, so the fastest trap speed leads.
                .chartYScale(domain: data.map(\.code))
                .frame(height: max(280, CGFloat(data.count) * Theme.Chart.rowHeight(typeSize)))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Speed trap chart")
                .accessibilityValue(data.map {
                    "\($0.code) \(Int($0.points.last?.speed ?? 0)) km/h"
                }.joined(separator: ", "))
            }
            .padding(Theme.Space.md)
        }
    }

    /// Names each dot, which size alone couldn't do.
    private var legend: some View {
        HStack(spacing: Theme.Space.md) {
            ForEach(["I1", "I2", "FL"], id: \.self) { label in
                HStack(spacing: 4) {
                    Circle().fill(Theme.Palette.textSecondary).frame(width: 6, height: 6)
                    Text(label)
                }
            }
            HStack(spacing: 4) {
                Circle().fill(Theme.Palette.textPrimary).frame(width: 11, height: 11)
                Text("ST (speed trap)")
            }
        }
        .font(.caption2)
        .foregroundStyle(Theme.Palette.textSecondary)
    }
}

// MARK: - View model

struct SpeedTrapPoint: Identifiable {
    let label: String
    let speed: Double
    var id: String { label }
}

struct SpeedTrapDriver: Identifiable {
    let code: String
    let teamColor: String?
    let points: [SpeedTrapPoint]
    var id: String { code }
}

@MainActor
final class SpeedTrapViewModel: ObservableObject {
    @Published var state: Loadable<[SpeedTrapDriver]> = .idle
    private var loadedKey: String?

    /// Speed range across every detection point, padded a little at each end.
    /// Anchoring at zero would compress the whole field — the story here is
    /// the 20 km/h spread between cars, not the distance from a standstill.
    /// The padding is generous because a dot sitting exactly on the domain
    /// edge overlaps the driver code in the axis gutter, and the topmost tick
    /// gets clipped by the plot's right edge.
    func speedDomain(_ data: [SpeedTrapDriver]) -> ClosedRange<Double> {
        let speeds = data.flatMap { $0.points.map(\.speed) }
        guard let lo = speeds.min(), let hi = speeds.max(), hi > lo else { return 0...350 }
        let pad = max((hi - lo) * 0.12, 4)
        return (lo - pad)...(hi + pad)
    }

    func load(year: Int, round: Int) async {
        let key = "\(year)-\(round)"
        if loadedKey == key, case .loaded = state { return }
        state = .loading
        do {
            let response = try await APIClient.shared.qualifyingSectors(year: year, round: round)
            let drivers = response.drivers.compactMap { driver -> SpeedTrapDriver? in
                guard let i1 = driver.speedI1, let i2 = driver.speedI2,
                      let fl = driver.speedFL, let st = driver.speedST else { return nil }
                return SpeedTrapDriver(
                    code: driver.code,
                    teamColor: driver.teamColor,
                    points: [
                        SpeedTrapPoint(label: "I1", speed: i1),
                        SpeedTrapPoint(label: "I2", speed: i2),
                        SpeedTrapPoint(label: "FL", speed: fl),
                        SpeedTrapPoint(label: "ST", speed: st),
                    ]
                )
            }.sorted { ($0.points.last?.speed ?? 0) > ($1.points.last?.speed ?? 0) }
            state = .loaded(drivers)
            loadedKey = key
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
