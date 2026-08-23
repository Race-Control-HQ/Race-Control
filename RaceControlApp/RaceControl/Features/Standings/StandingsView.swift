import SwiftUI

struct StandingsView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var vm = StandingsViewModel()
    @State private var mode: Mode = .drivers

    enum Mode: String, CaseIterable {
        case drivers = "Drivers", constructors = "Teams"
        case form = "Form", progression = "Progress", reliability = "Reliability"
        case wdc = "Title"
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                modeSelector

                Group {
                    switch mode {
                    case .drivers:
                        LoadableView(state: vm.driverState) {
                            await vm.loadDrivers(year: appState.selectedYear)
                        } content: { standings in
                            standingsList(standings)
                        }
                    case .constructors:
                        LoadableView(state: vm.constructorState) {
                            await vm.loadConstructors(year: appState.selectedYear)
                        } content: { standings in
                            constructorList(standings)
                        }
                    case .form:
                        SeasonFormGuideView(year: appState.selectedYear)
                    case .progression:
                        StandingsEvolutionView(year: appState.selectedYear)
                    case .reliability:
                        ReliabilityView(year: appState.selectedYear)
                    case .wdc:
                        WdcCalculatorView(year: appState.selectedYear)
                    }
                }
            }
            .navigationTitle("Standings")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { SeasonPicker() } }
            .background(Theme.Palette.background)
        }
        .task(id: appState.selectedYear) {
            await vm.loadDrivers(year: appState.selectedYear)
            await vm.loadConstructors(year: appState.selectedYear)
        }
    }

    private var modeSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Theme.Space.xs) {
                ForEach(Mode.allCases, id: \.self) { option in
                    let selected = option == mode
                    Button {
                        mode = option
                        Haptics.selection()
                    } label: {
                        Text(option.rawValue)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(selected ? .white : Theme.Palette.textSecondary)
                            .padding(.horizontal, Theme.Space.md)
                            .frame(minHeight: Theme.minTouch)
                            .background(selected ? Theme.Palette.racingRed : Theme.Palette.surfaceElevated,
                                        in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(.horizontal, Theme.Space.md)
        }
        .padding(.vertical, Theme.Space.sm)
    }

    private func standingsList(_ standings: [DriverStanding]) -> some View {
        ScrollView {
            LazyVStack(spacing: Theme.Space.sm) {
                ForEach(Array(standings.enumerated()), id: \.element.id) { index, s in
                    StandingRow(
                        rank: s.position?.intValue ?? index + 1,
                        title: s.fullName,
                        subtitle: s.teamName ?? "",
                        flag: CountryFlag.flag(country: s.nationality),
                        points: s.points?.numberLabel ?? "0",
                        wins: s.wins?.intValue ?? 0,
                        leaderPoints: standings.first?.points?.doubleValue,
                        myPoints: s.points?.doubleValue,
                        logoUrl: s.teamLogoUrl
                    )
                }
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
    }

    private func constructorList(_ standings: [ConstructorStanding]) -> some View {
        ScrollView {
            LazyVStack(spacing: Theme.Space.sm) {
                ForEach(Array(standings.enumerated()), id: \.element.id) { index, s in
                    StandingRow(
                        rank: s.position?.intValue ?? index + 1,
                        title: s.teamName ?? "",
                        subtitle: s.nationality ?? "",
                        flag: CountryFlag.flag(country: s.nationality),
                        points: s.points?.numberLabel ?? "0",
                        wins: s.wins?.intValue ?? 0,
                        leaderPoints: standings.first?.points?.doubleValue,
                        myPoints: s.points?.doubleValue,
                        logoUrl: s.teamLogoUrl
                    )
                }
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
    }
}

private struct StandingRow: View {
    let rank: Int
    let title: String
    let subtitle: String
    let flag: String
    let points: String
    let wins: Int
    let leaderPoints: Double?
    let myPoints: Double?
    var logoUrl: String? = nil

    private var isLeader: Bool { rank == 1 }

    var body: some View {
        VStack(spacing: Theme.Space.sm) {
            HStack(spacing: Theme.Space.md) {
                PositionBadge(text: String(rank), highlight: rank <= 3)
                TeamLogoView(url: logoUrl, size: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .lineLimit(1)
                    HStack(spacing: 4) {
                        Text(flag)
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(Theme.Palette.textSecondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                if wins > 0 {
                    Label("\(wins)", systemImage: "trophy.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color(hex: "FFD700"))
                }
                VStack(alignment: .trailing, spacing: 0) {
                    Text(points)
                        .font(.system(.title3, design: .rounded).weight(.bold)).monospacedDigit()
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text("PTS").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
                }
            }
            // Gap-to-leader progress bar
            if let leader = leaderPoints, leader > 0, let mine = myPoints {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.Palette.surfaceElevated)
                        Capsule().fill(isLeader ? Color(hex: "FFD700") : Theme.Palette.racingRed)
                            .frame(width: geo.size.width * CGFloat(mine / leader))
                    }
                }
                .frame(height: 4)
            }
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(Theme.Palette.stroke, lineWidth: 1))
    }
}

@MainActor
final class StandingsViewModel: ObservableObject {
    @Published var driverState: Loadable<[DriverStanding]> = .idle
    @Published var constructorState: Loadable<[ConstructorStanding]> = .idle
    private var driverYear: Int?
    private var constructorYear: Int?
    private let apiClient: APIClient

    init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    func loadDrivers(year: Int) async {
        if driverYear == year, case .loaded = driverState { return }
        driverState = .loading
        do {
            driverState = .loaded(try await apiClient.driverStandings(year: year))
            driverYear = year
        } catch {
            driverState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func loadConstructors(year: Int) async {
        if constructorYear == year, case .loaded = constructorState { return }
        constructorState = .loading
        do {
            constructorState = .loaded(try await apiClient.constructorStandings(year: year))
            constructorYear = year
        } catch {
            constructorState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
