import SwiftUI

struct TeamsView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var favorites: FavoritesStore
    @StateObject private var vm = TeamsViewModel()

    var body: some View {
        NavigationStack {
            LoadableView(state: vm.state) {
                await vm.load(year: appState.selectedYear)
            } content: { teams in
                if teams.isEmpty {
                    EmptyStateView(icon: "car.side", title: "No Teams",
                                   message: "No constructor data for \(appState.selectedYear).")
                } else {
                    ScrollView {
                        LazyVStack(spacing: Theme.Space.md) {
                            ForEach(pinFavorites(teams)) { team in
                                NavigationLink(value: team) {
                                    TeamCard(team: team,
                                             isFavorite: favorites.isFavoriteTeam(team.teamId),
                                             onToggleFavorite: { if let id = team.teamId { favorites.toggleTeam(id) } })
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(Theme.Space.md)
                    }
                    .background(Theme.Palette.background)
                    .refreshable { await vm.load(year: appState.selectedYear, force: true) }
                }
            }
            .navigationTitle("Constructors")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { SeasonPicker() } }
            .background(Theme.Palette.background)
            .navigationDestination(for: Team.self) { team in
                TeamDetailView(year: appState.selectedYear, team: team)
            }
        }
        .task(id: appState.selectedYear) { await vm.load(year: appState.selectedYear) }
    }

    private func pinFavorites(_ teams: [Team]) -> [Team] {
        teams.enumerated().sorted { a, b in
            let fa = favorites.isFavoriteTeam(a.element.teamId)
            let fb = favorites.isFavoriteTeam(b.element.teamId)
            if fa != fb { return fa }
            return a.offset < b.offset
        }.map(\.element)
    }
}

private struct TeamCard: View {
    let team: Team
    let isFavorite: Bool
    let onToggleFavorite: () -> Void
    private var accent: Color { .team(team.teamColor) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: Theme.Space.md) {
                Text(team.positionInt.map(String.init) ?? "–")
                    .font(.system(.largeTitle, design: .rounded, weight: .heavy))
                    .monospacedDigit()
                    .foregroundStyle(accent)
                    .frame(width: 44)
                TeamLogoView(url: team.teamLogoUrl, size: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(team.teamName ?? "Unknown")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .lineLimit(1)
                    Text("\(CountryFlag.flag(country: team.nationality)) \(team.nationality ?? "")")
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text(team.pointsString)
                        .font(.system(.title2, design: .rounded).weight(.bold)).monospacedDigit()
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text("POINTS").font(.caption2.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textTertiary)
                }
                FavoriteStar(isOn: isFavorite, action: onToggleFavorite, size: 18)
            }
            .padding(.vertical, Theme.Space.md)
            .padding(.leading, Theme.Space.md)
            .padding(.trailing, Theme.Space.sm)

            if !team.drivers.isEmpty {
                Divider().overlay(Theme.Palette.stroke)
                HStack(spacing: Theme.Space.lg) {
                    ForEach(team.drivers) { d in
                        HStack(spacing: Theme.Space.sm) {
                            DriverAvatar(url: d.headshotUrl, initials: d.code ?? "?", accent: accent, size: 34)
                            Text(d.code ?? d.name ?? "")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.Palette.textSecondary)
                        }
                    }
                    Spacer()
                }
                .padding(Theme.Space.md)
            }
        }
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(
                isFavorite ? Color(hex: "FFD700").opacity(0.6) : accent.opacity(0.4),
                lineWidth: isFavorite ? 1.5 : 1)
        )
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2).fill(accent).frame(width: 4).padding(.vertical, Theme.Space.md)
        }
    }
}

@MainActor
final class TeamsViewModel: ObservableObject {
    @Published var state: Loadable<[Team]> = .idle
    private var loadedYear: Int?

    func load(year: Int, force: Bool = false) async {
        if !force, loadedYear == year, case .loaded = state { return }
        state = .loading
        do {
            let teams = try await APIClient.shared.teams(year: year)
                .sorted { ($0.positionInt ?? 99) < ($1.positionInt ?? 99) }
            state = .loaded(teams)
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
