import SwiftUI

/// Driver x round finishing-position heatmap for the season. Turns the
/// existing per-round results into a single scannable form guide: streaks,
/// DNFs and momentum become a shape instead of a stack of separate results
/// screens. Tap a cell for that driver's result in that round.
struct SeasonFormGuideView: View {
    let year: Int
    @StateObject private var vm = SeasonFormGuideViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year)
        } content: { data in
            if data.rounds.isEmpty || data.drivers.isEmpty {
                EmptyStateView(icon: "square.grid.3x3.fill", title: "No Data",
                               message: "No completed races for \(String(year)) yet.")
            } else {
                content(data)
            }
        }
        .background(Theme.Palette.background)
        .task(id: year) { await vm.load(year: year) }
    }

    private func content(_ data: SeasonFormGuide) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            Text("Finishing position by round · tap a cell for detail")
                .font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                .padding(.horizontal, Theme.Space.md)

            grid(data)

            if let selection = vm.selection {
                selectionBar(selection)
            }

            legend
                .padding(.horizontal, Theme.Space.md)
        }
        .padding(.vertical, Theme.Space.sm)
    }

    // MARK: Grid

    private let rowHeight: CGFloat = 30
    private let cellWidth: CGFloat = 34
    private let nameColumnWidth: CGFloat = 56

    private func grid(_ data: SeasonFormGuide) -> some View {
        let roundLabels = Self.shortLabels(for: data.rounds)
        return HStack(alignment: .top, spacing: 0) {
            // Fixed leading column: driver codes, doesn't scroll horizontally.
            VStack(alignment: .leading, spacing: 2) {
                Color.clear.frame(height: rowHeight) // aligns with round-label header row
                ForEach(data.drivers) { driver in
                    Text(driver.code)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .frame(width: nameColumnWidth, height: rowHeight, alignment: .leading)
                }
            }
            .padding(.leading, Theme.Space.md)

            ScrollView(.horizontal, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 2) {
                        ForEach(data.rounds) { round in
                            Text(roundLabels[round.round] ?? "R\(round.round)")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(Theme.Palette.textTertiary)
                                .frame(width: cellWidth, height: rowHeight)
                        }
                    }
                    ForEach(data.drivers) { driver in
                        HStack(spacing: 2) {
                            ForEach(data.rounds) { round in
                                cell(driver: driver, round: round)
                            }
                        }
                    }
                }
                .padding(.trailing, Theme.Space.md)
            }
        }
    }

    private func cell(driver: SeasonFormDriver, round: RaceEvent) -> some View {
        let entry = driver.cells[round.round]
        let tier = FormTier.tier(for: entry)
        return Button {
            vm.select(driver: driver, round: round, entry: entry)
        } label: {
            Text(tier.label(for: entry))
                .font(.caption2.weight(.bold)).monospacedDigit()
                .foregroundStyle(tier.textColor)
                .frame(width: cellWidth, height: rowHeight)
                .background(tier.background, in: RoundedRectangle(cornerRadius: 5))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(driver.name), \(round.displayName), \(tier.accessibilityLabel(for: entry))")
    }

    private func selectionBar(_ selection: FormGuideSelection) -> some View {
        HStack(spacing: Theme.Space.sm) {
            Image(systemName: "flag.checkered")
                .foregroundStyle(Theme.Palette.racingRed)
            Text("\(selection.driverName) · \(selection.roundName) · \(selection.resultLabel)")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.Palette.textPrimary)
                .lineLimit(1)
            Spacer()
        }
        .padding(Theme.Space.sm)
        .padding(.horizontal, Theme.Space.sm)
        .background(Theme.Palette.surfaceElevated, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
        .padding(.horizontal, Theme.Space.md)
        .accessibilityElement(children: .combine)
    }

    private var legend: some View {
        HStack(spacing: Theme.Space.md) {
            legendSwatch("P1–3", FormTier.podium.background)
            legendSwatch("P4–10", FormTier.points.background)
            legendSwatch("P11+", FormTier.outsidePoints.background)
            legendSwatch("DNF/DSQ", FormTier.dnf.background)
        }
        .font(.caption2)
        .foregroundStyle(Theme.Palette.textSecondary)
    }

    private func legendSwatch(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 12, height: 12)
            Text(label)
        }
    }

    /// Short column labels for each round, de-duplicated across the whole
    /// season: a plain 3-letter prefix collides often (Montréal/Monte Carlo,
    /// United States/United Kingdom, Australia/Austria all share a prefix),
    /// so widen the colliding label until it's unique, falling back to the
    /// round number if it still can't be disambiguated.
    static func shortLabels(for rounds: [RaceEvent]) -> [Int: String] {
        var used: Set<String> = []
        var labels: [Int: String] = [:]
        for round in rounds {
            let source = (round.location ?? round.country ?? round.name ?? "R\(round.round)")
                .filter(\.isLetter)
            var length = min(3, source.count)
            var label = length > 0 ? String(source.prefix(length)).uppercased() : "R\(round.round)"
            while used.contains(label) && length < source.count {
                length += 1
                label = String(source.prefix(length)).uppercased()
            }
            if used.contains(label) { label = "R\(round.round)" }
            used.insert(label)
            labels[round.round] = label
        }
        return labels
    }
}

// MARK: - Tiering

private enum FormTier {
    case podium, points, outsidePoints, dnf, unknown

    static func tier(for entry: ResultEntry?) -> FormTier {
        guard let entry else { return .unknown }
        if let status = entry.status, !ResultStatus.isFinish(status) { return .dnf }
        guard let position = entry.position ?? Double(entry.classifiedPosition ?? "") else { return .unknown }
        if position <= 3 { return .podium }
        if position <= 10 { return .points }
        return .outsidePoints
    }

    func label(for entry: ResultEntry?) -> String {
        guard let entry else { return "–" }
        if let status = entry.status, !ResultStatus.isFinish(status) {
            return ResultStatus.shortCode(status)
        }
        return entry.positionLabel
    }

    func accessibilityLabel(for entry: ResultEntry?) -> String {
        guard let entry else { return "no result" }
        if let status = entry.status, !ResultStatus.isFinish(status) {
            return status
        }
        return "P\(entry.positionLabel)"
    }

    var background: Color {
        switch self {
        case .podium: Color(hex: "E1AD25")
        case .points: Theme.Palette.positive.opacity(0.55)
        case .outsidePoints: Theme.Palette.surfaceElevated
        case .dnf: Theme.Palette.negative.opacity(0.65)
        case .unknown: Theme.Palette.surface
        }
    }

    var textColor: Color {
        switch self {
        case .podium: .black
        case .points, .dnf: .white
        case .outsidePoints: Theme.Palette.textPrimary
        case .unknown: Theme.Palette.textTertiary
        }
    }
}

private enum ResultStatus {
    static func isFinish(_ status: String) -> Bool {
        let s = status.lowercased()
        return s.isEmpty || s == "finished" || s.hasPrefix("+")
    }
    static func shortCode(_ status: String) -> String {
        let s = status.lowercased()
        if s.contains("disqualified") { return "DSQ" }
        if s.contains("did not start") { return "DNS" }
        if s.contains("did not qualify") { return "DNQ" }
        return "DNF"
    }
}


// MARK: - View model

struct SeasonFormGuide {
    let rounds: [RaceEvent]
    let drivers: [SeasonFormDriver]
}

struct SeasonFormDriver: Identifiable {
    let id: String
    let name: String
    let code: String
    let cells: [Int: ResultEntry]
}

struct FormGuideSelection: Identifiable {
    var id: String { driverName + roundName }
    let driverName: String
    let roundName: String
    let resultLabel: String
}

@MainActor
final class SeasonFormGuideViewModel: ObservableObject {
    @Published var state: Loadable<SeasonFormGuide> = .idle
    @Published var selection: FormGuideSelection?
    private var loadedYear: Int?

    func load(year: Int) async {
        if loadedYear == year, case .loaded = state { return }
        state = .loading
        do {
            let schedule = try await APIClient.shared.schedule(year: year)
            let completedRounds = schedule.filter(\.completed).sorted { $0.round < $1.round }
            guard !completedRounds.isEmpty else {
                state = .loaded(SeasonFormGuide(rounds: [], drivers: []))
                loadedYear = year
                return
            }

            var resultsByRound: [Int: [String: ResultEntry]] = [:]
            try await withThrowingTaskGroup(of: (Int, SessionResultsResponse).self) { group in
                for event in completedRounds {
                    group.addTask {
                        let response = try await APIClient.shared.results(year: year, round: event.round, session: "R")
                        return (event.round, response)
                    }
                }
                for try await (round, response) in group {
                    var byDriver: [String: ResultEntry] = [:]
                    for entry in response.results {
                        let key = entry.driverId ?? entry.abbreviation ?? entry.fullName ?? UUID().uuidString
                        byDriver[key] = entry
                    }
                    resultsByRound[round] = byDriver
                }
            }

            var rosterOrder: [String] = []
            var meta: [String: (name: String, code: String)] = [:]
            for event in completedRounds {
                guard let byDriver = resultsByRound[event.round] else { continue }
                for (key, entry) in byDriver where meta[key] == nil {
                    rosterOrder.append(key)
                    meta[key] = (entry.fullName ?? entry.abbreviation ?? key,
                                 entry.abbreviation ?? String(key.prefix(3)).uppercased())
                }
            }

            if let standings = try? await APIClient.shared.driverStandings(year: year) {
                let order = standings.compactMap(\.driverId)
                rosterOrder.sort { a, b in
                    let ia = order.firstIndex(of: a) ?? Int.max
                    let ib = order.firstIndex(of: b) ?? Int.max
                    return ia != ib ? ia < ib : a < b
                }
            }

            let drivers = rosterOrder.map { key -> SeasonFormDriver in
                let m = meta[key]!
                var cells: [Int: ResultEntry] = [:]
                for event in completedRounds {
                    if let entry = resultsByRound[event.round]?[key] {
                        cells[event.round] = entry
                    }
                }
                return SeasonFormDriver(id: key, name: m.name, code: m.code, cells: cells)
            }

            state = .loaded(SeasonFormGuide(rounds: completedRounds, drivers: drivers))
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func select(driver: SeasonFormDriver, round: RaceEvent, entry: ResultEntry?) {
        Haptics.selection()
        let resultLabel: String
        if let entry {
            if let status = entry.status, !ResultStatus.isFinish(status) {
                resultLabel = status
            } else {
                resultLabel = "P\(entry.positionLabel)"
            }
        } else {
            resultLabel = "No result"
        }
        selection = FormGuideSelection(driverName: driver.name, roundName: round.displayName, resultLabel: resultLabel)
    }
}
