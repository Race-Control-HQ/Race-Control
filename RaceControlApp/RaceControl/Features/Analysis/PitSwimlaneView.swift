import SwiftUI

/// Pit stops aligned on a shared lap axis, with position before/after the
/// stop and whether it was an undercut, overcut, or held position — turning
/// strategy into visible cause and effect instead of a ledger of times.
struct PitSwimlaneView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = PitSwimlaneViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { data in
            if data.stops.isEmpty {
                EmptyStateView(icon: "arrow.triangle.branch", title: "No Pit-Stop Data",
                               message: "No timed pit-lane transits are available for this race.")
            } else {
                content(data)
            }
        }
        .navigationTitle("Pit Swimlane")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task(id: "\(year)-\(round)") { await vm.load(year: year, round: round) }
    }

    private func content(_ data: PitSwimlaneData) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.md) {
                Text("Lap \(data.minLap)–\(data.maxLap) · entry → rejoin position · tap a stop for detail")
                    .font(.caption).foregroundStyle(Theme.Palette.textSecondary)

                VStack(spacing: Theme.Space.sm) {
                    ForEach(data.stops) { stop in
                        SwimlaneRow(stop: stop, minLap: data.minLap, maxLap: data.maxLap) {
                            vm.select(stop)
                        }
                    }
                }

                axis(data)

                if let selection = vm.selection {
                    detailCard(selection)
                }
            }
            .padding(Theme.Space.md)
        }
    }

    private func axis(_ data: PitSwimlaneData) -> some View {
        HStack {
            Text("L\(data.minLap)").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            Spacer()
            Text("L\((data.minLap + data.maxLap) / 2)").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            Spacer()
            Text("L\(data.maxLap)").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
        }
        .padding(.leading, 60)
    }

    private func detailCard(_ stop: PitStopLedgerItem) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(stop.driverCode) · Stop \(stop.stop) · Lap \(stop.lap)")
                .font(.subheadline.weight(.bold)).foregroundStyle(Theme.Palette.textPrimary)
            Text("Pit-lane loss \(String(format: "%.1f", Double(stop.lossMs) / 1000))s · \(stop.outcome.capitalized) · P\(stop.entryPosition.map(String.init) ?? "–") → P\(stop.rejoinPosition.map(String.init) ?? "–")")
                .font(.caption).foregroundStyle(Theme.Palette.textSecondary)
            if !stop.rivals.isEmpty {
                Text("Rival window: \(stop.rivals.joined(separator: ", "))")
                    .font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.Space.sm)
        .background(Theme.Palette.surfaceElevated, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
    }
}

private struct SwimlaneRow: View {
    let stop: PitStopLedgerItem
    let minLap: Int
    let maxLap: Int
    let onTap: () -> Void

    private var pitFraction: CGFloat {
        let span = max(maxLap - minLap, 1)
        return CGFloat(stop.lap - minLap) / CGFloat(span)
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Theme.Space.sm) {
                Text(stop.driverCode)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .frame(width: 42, alignment: .leading)

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.Palette.surfaceElevated).frame(height: 14)
                        let pitX = geo.size.width * pitFraction
                        Capsule().fill(Color.team(stop.teamColor).opacity(0.6))
                            .frame(width: max(pitX, 2), height: 4)
                            .offset(y: 5)
                        Capsule().fill(Theme.Palette.info.opacity(0.6))
                            .frame(width: max(geo.size.width - pitX, 2), height: 4)
                            .offset(x: pitX, y: 5)
                        Rectangle().fill(Theme.Palette.racingRed)
                            .frame(width: 2, height: 20)
                            .offset(x: pitX - 1, y: -3)
                    }
                }
                .frame(height: 14)

                positionBadge
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(stop.driverCode), lap \(stop.lap), \(stop.outcome), \(positionSummary)")
    }

    private var positionSummary: String {
        "P\(stop.entryPosition.map(String.init) ?? "–") to P\(stop.rejoinPosition.map(String.init) ?? "–")"
    }

    private var positionBadge: some View {
        let gained = stop.positionsGained ?? 0
        return Text(positionSummary)
            .font(.caption2.weight(.bold)).monospacedDigit()
            .foregroundStyle(gained > 0 ? Theme.Palette.positive : (gained < 0 ? Theme.Palette.racingRedText : Theme.Palette.textSecondary))
            .frame(width: 76, alignment: .trailing)
    }
}

// MARK: - View model

struct PitSwimlaneData {
    let stops: [PitStopLedgerItem]
    let minLap: Int
    let maxLap: Int
}

@MainActor
final class PitSwimlaneViewModel: ObservableObject {
    @Published var state: Loadable<PitSwimlaneData> = .idle
    @Published var selection: PitStopLedgerItem?
    private var loadedKey: String?

    func load(year: Int, round: Int) async {
        let key = "\(year)-\(round)"
        if loadedKey == key, case .loaded = state { return }
        state = .loading
        do {
            let response = try await APIClient.shared.pitStops(year: year, round: round)
            let stops = response.stops.sorted { $0.lap < $1.lap }
            let laps = stops.map(\.lap)
            let minLap = max((laps.min() ?? 1) - 1, 0)
            let maxLap = (laps.max() ?? 1) + 1
            state = .loaded(PitSwimlaneData(stops: stops, minLap: minLap, maxLap: maxLap))
            loadedKey = key
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func select(_ stop: PitStopLedgerItem) {
        Haptics.selection()
        selection = stop
    }
}
