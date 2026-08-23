import SwiftUI
import Charts

struct DriverDetailView: View {
    let year: Int
    let driver: Driver
    @EnvironmentObject private var favorites: FavoritesStore
    @StateObject private var vm = DriverDetailViewModel()

    private var accent: Color { .team(driver.teamColor) }

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Space.md) {
                hero
                statsRow
                pointsProgressionChart
                DriverFingerprintView(year: year, driverId: driver.driverId, accent: accent)
                seasonResults
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
        .navigationTitle(driver.code ?? driver.familyName ?? "Driver")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                FavoriteStar(isOn: favorites.isFavoriteDriver(driver.driverId),
                             action: { favorites.toggleDriver(driver.driverId) }, size: 18)
            }
        }
        .task { await vm.load(year: year, driverId: driver.driverId) }
    }

    private var hero: some View {
        VStack(spacing: Theme.Space.md) {
            DriverAvatar(url: driver.headshotUrl, initials: driver.code ?? "?", accent: accent, size: 120)
            VStack(spacing: 4) {
                Text(driver.fullName)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .multilineTextAlignment(.center)
                if let canWin = vm.canWinWdc {
                    NavigationLink {
                        WdcCalculatorView(year: year)
                    } label: {
                        Text(canWin ? "Can win WDC" : "Can't win WDC")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(canWin ? Theme.Palette.positive : Theme.Palette.textTertiary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                (canWin ? Theme.Palette.positive : Theme.Palette.textTertiary).opacity(0.15),
                                in: Capsule()
                            )
                    }
                }
                HStack(spacing: Theme.Space.sm) {
                    if let num = driver.numberString {
                        Text("#\(num)").font(.headline).foregroundStyle(accent)
                    }
                    TeamLogoView(url: driver.teamLogoUrl, size: 18)
                    Text(driver.teamName ?? "")
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
                Text("\(CountryFlag.flag(country: driver.nationality, code: driver.countryCode)) \(driver.nationality ?? "")")
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Theme.Space.lg)
        .background(
            LinearGradient(colors: [accent.opacity(0.30), Theme.Palette.surface],
                           startPoint: .top, endPoint: .bottom),
            in: RoundedRectangle(cornerRadius: Theme.Radius.lg)
        )
    }

    private var statsRow: some View {
        Card {
            HStack {
                StatCell(value: driver.positionInt.map(String.init) ?? "–", label: "Championship")
                Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                StatCell(value: driver.pointsString, label: "Points", accent: accent)
                Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                StatCell(value: driver.wins?.numberLabel ?? "0", label: "Wins")
                if let dob = age(from: driver.dateOfBirth) {
                    Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                    StatCell(value: dob, label: "Age")
                }
            }
        }
    }

    /// Cumulative championship points, round by round. Reuses the same
    /// `/api/standings-evolution` data as the Standings page's Progress
    /// chart, just for this one driver instead of a multi-driver legend.
    @ViewBuilder private var pointsProgressionChart: some View {
        if !vm.pointsSeries.isEmpty {
            Card {
                VStack(alignment: .leading, spacing: Theme.Space.sm) {
                    Text("POINTS PROGRESSION")
                        .font(.caption.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Chart {
                        ForEach(vm.pointsSeries, id: \.round) { pt in
                            LineMark(x: .value("Round", pt.round), y: .value("Points", pt.points))
                                .foregroundStyle(accent)
                                .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                                 lineCap: .round, lineJoin: .round))
                                .interpolationMethod(.monotone)
                        }
                        if let last = vm.pointsSeries.last {
                            PointMark(x: .value("Round", last.round), y: .value("Points", last.points))
                                .foregroundStyle(accent)
                                .symbolSize(Theme.Chart.pointSize)
                        }
                    }
                    .chartXAxis {
                        AxisMarks { _ in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
                        }
                    }
                    .chartYAxis {
                        AxisMarks { _ in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
                        }
                    }
                    .frame(height: 180)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Points progression chart")
                    .accessibilityValue(
                        vm.pointsSeries.map { "Round \($0.round): \(Int($0.points)) points" }.joined(separator: ", ")
                    )
                }
            }
        }
    }

    @ViewBuilder private var seasonResults: some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            Text("SEASON RESULTS")
                .font(.caption.weight(.bold)).tracking(1)
                .foregroundStyle(Theme.Palette.textSecondary)

            switch vm.state {
            case .idle, .loading:
                ProgressView().tint(accent).frame(maxWidth: .infinity).padding()
            case .failed(let msg):
                Text(msg).font(.footnote).foregroundStyle(Theme.Palette.textSecondary)
            case .loaded(let detail):
                let results = detail.seasonResults ?? []
                if results.isEmpty {
                    Text("No race results yet this season.")
                        .font(.subheadline).foregroundStyle(Theme.Palette.textSecondary)
                } else {
                    formChart(results)
                    ForEach(results) { r in
                        HStack {
                            Text("R\(r.round ?? 0)")
                                .font(.caption.weight(.bold)).monospacedDigit()
                                .foregroundStyle(Theme.Palette.textTertiary).frame(width: 34, alignment: .leading)
                            Text(r.raceName ?? "")
                                .font(.subheadline).foregroundStyle(Theme.Palette.textPrimary).lineLimit(1)
                            Spacer()
                            if let status = r.status, status != "Finished", r.positionInt == nil {
                                Text(status).font(.caption).foregroundStyle(Theme.Palette.negative)
                            }
                            Text("P\(r.positionInt.map(String.init) ?? "–")")
                                .font(.subheadline.weight(.semibold)).monospacedDigit()
                                .foregroundStyle(podiumColor(r.positionInt))
                            if r.pointsLabel != "0" {
                                Text("+\(r.pointsLabel)").font(.caption.weight(.bold))
                                    .foregroundStyle(accent).frame(width: 34, alignment: .trailing)
                            } else {
                                Spacer().frame(width: 34)
                            }
                        }
                        .padding(.vertical, 8).padding(.horizontal, Theme.Space.sm)
                        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                    }
                }
            }
        }
    }

    /// A compact finishing-position sparkline across the season.
    private func formChart(_ results: [DriverSeasonResult]) -> some View {
        let positions = results.compactMap { $0.positionInt }
        let maxPos = max(positions.max() ?? 20, 1)
        return HStack(alignment: .bottom, spacing: 3) {
            ForEach(results) { r in
                let p = r.positionInt ?? maxPos
                let height = CGFloat(maxPos - p + 1) / CGFloat(maxPos) * 60
                RoundedRectangle(cornerRadius: 2)
                    .fill(podiumColor(r.positionInt))
                    .frame(height: max(height, 4))
            }
        }
        .frame(height: 64)
        .padding(Theme.Space.sm)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
    }

    private func podiumColor(_ pos: Int?) -> Color {
        switch pos {
        case 1: return Color(hex: "FFD700")
        case 2: return Color(hex: "C0C0C0")
        case 3: return Color(hex: "CD7F32")
        case .some: return Theme.Palette.textPrimary
        default: return Theme.Palette.textTertiary
        }
    }

    private func age(from dob: String?) -> String? {
        guard let date = ISO8601.flexible(dob) else { return nil }
        let years = Calendar.current.dateComponents([.year], from: date, to: Date()).year
        return years.map(String.init)
    }
}

@MainActor
final class DriverDetailViewModel: ObservableObject {
    @Published var state: Loadable<DriverDetail> = .idle
    /// This driver's cumulative points, round by round, for the progression
    /// chart. Empty until the secondary fetch below resolves.
    @Published var pointsSeries: [EvolutionPoint] = []
    /// nil until the secondary fetch resolves, or if the driver isn't in the
    /// WDC calculator's data for this year (e.g. no standings on record).
    @Published var canWinWdc: Bool?

    func load(year: Int, driverId: String) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.driverDetail(year: year, driverId: driverId))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
        // Secondary lookups for the points-progression chart and the WDC
        // badge; failure shouldn't block the main screen.
        if let evolution = try? await APIClient.shared.standingsEvolution(year: year) {
            pointsSeries = evolution.drivers.first(where: { $0.driverId == driverId })?.series ?? []
        }
        if let wdc = try? await APIClient.shared.wdcCalculator(year: year) {
            canWinWdc = wdc.drivers.first(where: { $0.driverId == driverId })?.canWin
        }
    }
}
