import SwiftUI

struct DriversView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var favorites: FavoritesStore
    @StateObject private var vm = DriversViewModel()
    @State private var query = ""
    @State private var showCompare = false

    var body: some View {
        NavigationStack {
            LoadableView(state: vm.state) {
                await vm.load(year: appState.selectedYear)
            } content: { drivers in
                let filtered = pinFavorites(filter(drivers))
                if drivers.isEmpty {
                    EmptyStateView(icon: "person.slash", title: "No Drivers",
                                   message: "No driver data for \(appState.selectedYear).")
                } else {
                    ScrollView {
                        LazyVStack(spacing: Theme.Space.sm) {
                            ForEach(filtered) { driver in
                                NavigationLink(value: DriverRoute(year: appState.selectedYear, driver: driver)) {
                                    DriverRow(driver: driver,
                                              isFavorite: favorites.isFavoriteDriver(driver.driverId),
                                              onToggleFavorite: { favorites.toggleDriver(driver.driverId) })
                                }
                                .buttonStyle(.plain)
                            }
                            if filtered.isEmpty {
                                Text("No drivers match “\(query)”.")
                                    .font(.subheadline)
                                    .foregroundStyle(Theme.Palette.textSecondary)
                                    .padding(.top, Theme.Space.xl)
                            }
                        }
                        .padding(Theme.Space.md)
                    }
                    .background(Theme.Palette.background)
                    .refreshable { await vm.load(year: appState.selectedYear, force: true) }
                }
            }
            .navigationTitle("Drivers")
            .searchable(text: $query, prompt: "Search drivers or teams")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showCompare = true
                    } label: {
                        Image(systemName: "person.2.badge.gearshape")
                    }
                    .accessibilityLabel("Compare drivers")
                }
                ToolbarItem(placement: .topBarTrailing) { SeasonPicker() }
            }
            .sheet(isPresented: $showCompare) {
                HeadToHeadView(year: appState.selectedYear)
            }
            .background(Theme.Palette.background)
            .navigationDestination(for: DriverRoute.self) { route in
                DriverDetailView(year: route.year, driver: route.driver)
            }
        }
        .task(id: appState.selectedYear) { await vm.load(year: appState.selectedYear) }
    }

    private func filter(_ drivers: [Driver]) -> [Driver] {
        guard !query.isEmpty else { return drivers }
        let q = query.lowercased()
        return drivers.filter {
            $0.fullName.lowercased().contains(q)
            || ($0.code?.lowercased().contains(q) ?? false)
            || ($0.teamName?.lowercased().contains(q) ?? false)
            || ($0.numberString?.contains(q) ?? false)
        }
    }

    /// Stable sort that lifts favourites to the top, preserving championship order.
    private func pinFavorites(_ drivers: [Driver]) -> [Driver] {
        drivers.enumerated().sorted { a, b in
            let fa = favorites.isFavoriteDriver(a.element.driverId)
            let fb = favorites.isFavoriteDriver(b.element.driverId)
            if fa != fb { return fa }
            return a.offset < b.offset
        }.map(\.element)
    }
}

struct DriverRoute: Hashable {
    let year: Int
    let driver: Driver
}

private struct DriverRow: View {
    let driver: Driver
    let isFavorite: Bool
    let onToggleFavorite: () -> Void
    private var accent: Color { .team(driver.teamColor) }

    var body: some View {
        // Two lines instead of one: the old single-row layout packed position,
        // accent bar, avatar, name, points, flag and the favourite star into
        // one HStack, which left the name competing on equal footing with
        // every other flexible element for whatever width was left after the
        // fixed-width ones, on a phone-width screen that was often well
        // under 100pt, so long driver names truncated to a handful of
        // characters. Splitting name+favourite onto their own top line (with
        // number/team/points/flag on a second line below) gives the name
        // nearly the whole row width to itself.
        HStack(alignment: .top, spacing: Theme.Space.md) {
            if let pos = driver.positionInt {
                Text("\(pos)")
                    .font(.system(.headline, design: .rounded).weight(.bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(width: 26)
                    .padding(.top, 2)
            }
            TeamAccentBar(color: accent).frame(height: 56)
            DriverAvatar(url: driver.headshotUrl, initials: driver.code ?? "?", accent: accent, size: 48)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: Theme.Space.sm) {
                    Text(driver.fullName)
                        .font(.headline)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .lineLimit(1)
                        .layoutPriority(1)
                    Spacer(minLength: 4)
                    FavoriteStar(isOn: isFavorite, action: onToggleFavorite, size: 18)
                }
                HStack(spacing: 6) {
                    if let num = driver.numberString {
                        Text("#\(num)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(accent)
                    }
                    TeamLogoView(url: driver.teamLogoUrl, size: 16)
                    Text(driver.teamName ?? "")
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(driver.pointsString)
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .monospacedDigit()
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text("PTS").font(.caption2.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textTertiary)
                    Text(CountryFlag.flag(country: driver.nationality, code: driver.countryCode))
                        .font(.subheadline)
                }
            }
        }
        .padding(.vertical, Theme.Space.sm)
        .padding(.trailing, Theme.Space.sm)
        .padding(.leading, Theme.Space.sm)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(
            isFavorite ? Color(hex: "FFD700").opacity(0.4) : Theme.Palette.stroke, lineWidth: 1))
    }
}

@MainActor
final class DriversViewModel: ObservableObject {
    @Published var state: Loadable<[Driver]> = .idle
    private var loadedYear: Int?

    func load(year: Int, force: Bool = false) async {
        if !force, loadedYear == year, case .loaded = state { return }
        state = .loading
        do {
            let drivers = try await APIClient.shared.drivers(year: year)
                .sorted { ($0.positionInt ?? 99) < ($1.positionInt ?? 99) }
            state = .loaded(drivers)
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
