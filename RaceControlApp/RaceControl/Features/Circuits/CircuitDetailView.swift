import SwiftUI

/// Rich circuit page: track map, key stats (length, corners, laps), the race's
/// fastest lap and podium, plus quick actions to replay or view full results.
struct CircuitDetailView: View {
    let year: Int
    let round: Int
    let circuit: Circuit
    let event: RaceEvent?

    @StateObject private var vm = CircuitDetailViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Space.md) {
                header
                LoadableView(state: vm.state) {
                    await vm.load(year: year, round: round)
                } content: { map in
                    VStack(spacing: Theme.Space.md) {
                        mapCard(map)
                        statsCard(map)
                        if let fl = map.fastestLap, fl.time != nil {
                            fastestLapCard(fl)
                        }
                        if !vm.podium.isEmpty {
                            podiumCard
                        }
                        actionButtons
                    }
                }
                .frame(minHeight: 300)
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
        .navigationTitle(circuit.name ?? "Circuit")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load(year: year, round: round) }
    }

    // MARK: Header
    private var header: some View {
        Card {
            HStack(spacing: Theme.Space.md) {
                Text(CountryFlag.flag(country: circuit.country))
                    .font(.system(size: 48))
                VStack(alignment: .leading, spacing: 4) {
                    Text(circuit.name ?? "Circuit")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text([circuit.locality, circuit.country].compactMap { $0 }.joined(separator: ", "))
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    if let event {
                        HStack(spacing: 6) {
                            Text("Round \(event.round)")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Theme.Palette.racingRedText)
                            if let date = event.parsedDate {
                                Text("· \(date.formatted(date: .abbreviated, time: .omitted))")
                                    .font(.caption)
                                    .foregroundStyle(Theme.Palette.textTertiary)
                            }
                        }
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    // MARK: Map
    @ViewBuilder
    private func mapCard(_ map: CircuitMap) -> some View {
        if map.outline.isEmpty {
            EmptyStateView(icon: "map", title: "No Track Map",
                           message: "Positional data isn't available for this circuit.")
                .frame(height: 220)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.lg))
        } else {
            VStack(spacing: Theme.Space.sm) {
                Group {
                    if (map.points?.count ?? 0) > 1 {
                        RichTrackMap(map: map)
                    } else {
                        TrackShape(map: map)
                    }
                }
                .padding(Theme.Space.md)
                .frame(maxWidth: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.lg))

                if (map.points?.count ?? 0) > 1 {
                    SpeedLegend().padding(.horizontal, Theme.Space.sm)
                }
            }
        }
    }

    // MARK: Stats
    private func statsCard(_ map: CircuitMap) -> some View {
        Card {
            HStack {
                StatCell(value: lengthLabel(map.lengthMeters), label: "Length")
                Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                StatCell(value: map.corners.isEmpty ? "–" : "\(map.corners.count)", label: "Corners")
                Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                StatCell(value: vm.totalLaps.map(String.init) ?? "–", label: "Race Laps")
            }
        }
    }

    private func fastestLapCard(_ fl: CircuitFastestLap) -> some View {
        let accent = Color.team(fl.teamColor)
        return Card {
            HStack(spacing: Theme.Space.md) {
                Image(systemName: "stopwatch.fill")
                    .font(.title2)
                    .foregroundStyle(Theme.Palette.info)
                    .frame(width: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text("FASTEST RACE LAP")
                        .font(.caption2.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Text(fl.driverName ?? fl.driver ?? "–")
                        .font(.headline)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    if let team = fl.team {
                        HStack(spacing: 4) {
                            TeamLogoView(url: fl.teamLogoUrl, size: 14)
                            Text(team).font(.caption).foregroundStyle(accent)
                        }
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text(fl.time ?? "–")
                        .font(.system(.title3, design: .monospaced).weight(.bold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                    if fl.compound != nil {
                        TyreBadge(compound: fl.compound, size: 22)
                    }
                }
            }
        }
    }

    private var podiumCard: some View {
        Card {
            VStack(alignment: .leading, spacing: Theme.Space.sm) {
                Text("PODIUM")
                    .font(.caption.weight(.bold)).tracking(1)
                    .foregroundStyle(Theme.Palette.textSecondary)
                ForEach(vm.podium) { entry in
                    HStack(spacing: Theme.Space.md) {
                        PositionBadge(text: entry.positionLabel, highlight: true)
                        DriverAvatar(url: entry.headshotUrl, initials: entry.abbreviation ?? "?",
                                     accent: .team(entry.teamColor), size: 34)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(entry.fullName ?? entry.abbreviation ?? "")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.Palette.textPrimary)
                            Text(entry.teamName ?? "")
                                .font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                        }
                        Spacer()
                    }
                }
            }
        }
    }

    // MARK: Actions
    private var actionButtons: some View {
        HStack(spacing: Theme.Space.md) {
            NavigationLink {
                ReplayView(year: year, round: round, title: circuit.name ?? "Race")
            } label: {
                actionLabel("Race Replay", system: "play.circle.fill", tint: Theme.Palette.racingRed)
            }
            if let event {
                NavigationLink {
                    RaceDetailView(event: event)
                } label: {
                    actionLabel("Full Results", system: "list.number", tint: Theme.Palette.info)
                }
            }
        }
    }

    private func actionLabel(_ text: String, system: String, tint: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: system).font(.title2)
            Text(text).font(.footnote.weight(.semibold))
        }
        .foregroundStyle(tint)
        .frame(maxWidth: .infinity, minHeight: Theme.minTouch + 24)
        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private func lengthLabel(_ meters: Double?) -> String {
        guard let m = meters, m > 0 else { return "–" }
        return String(format: "%.3f km", m / 1000)
    }
}

@MainActor
final class CircuitDetailViewModel: ObservableObject {
    @Published var state: Loadable<CircuitMap> = .idle
    @Published var podium: [ResultEntry] = []
    @Published var totalLaps: Int?

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            let map = try await APIClient.shared.circuitMap(year: year, round: round)
            state = .loaded(map)
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
        // Race results are supplementary: a failure here shouldn't block the map.
        if let results = try? await APIClient.shared.results(year: year, round: round, session: "R") {
            podium = Array(results.results.prefix(3))
            totalLaps = results.totalLaps?.intValue
        }
    }
}
