import SwiftUI

struct RaceDetailView: View {
    let event: RaceEvent
    @StateObject private var vm = RaceDetailViewModel()
    @State private var selectedSession: String

    init(event: RaceEvent) {
        self.event = event
        _selectedSession = State(initialValue: Self.defaultSession(for: event))
    }

    /// Sessions that produce a classification worth showing, and the label
    /// each one gets in the picker.
    private static let sessionLabels: [String: String] = [
        "R": "Race", "Q": "Quali", "S": "Sprint", "SQ": "Sprint Q", "SS": "Sprint Q",
        "FP1": "FP1", "FP2": "FP2", "FP3": "FP3",
    ]

    private var resultSessions: [(label: String, id: String)] {
        let out = event.sessions.compactMap { s -> (label: String, id: String)? in
            guard let id = s.identifier, let label = Self.sessionLabels[id] else { return nil }
            return (label, id)
        }
        // Race first, then the rest of the weekend in the order it ran.
        return out.filter { $0.id == "R" } + out.filter { $0.id != "R" }
    }

    /// Which session to open on: the most recent one to have finished.
    ///
    /// Defaulting to the race is right for a weekend that's been run, but
    /// mid-weekend it opens on a classification that doesn't exist yet and
    /// hides the practice and qualifying results that do. The feed lists
    /// sessions in weekend order, so the last finished one is the most recent
    /// thing to have happened.
    private static func defaultSession(for event: RaceEvent) -> String {
        let run = event.sessions.filter {
            guard let id = $0.identifier, sessionLabels[id] != nil else { return false }
            return hasFinished($0)
        }
        return run.last?.identifier ?? "R"
    }

    /// Assumed session length, used to tell a session that has finished from
    /// one still running: the schedule feed only carries start times, so an end
    /// time has to be inferred. Deliberately generous — a session that overruns
    /// (a delayed start, a long red flag) should read as still running rather
    /// than flip to finished while the cars are on track. Mirrors
    /// `sessionDurationMs` in the web app's `lib/weekend.ts`.
    private static func hasFinished(_ session: EventSession, now: Date = Date()) -> Bool {
        guard let start = ISO8601.flexible(session.date) else { return false }
        let duration: TimeInterval = session.identifier == "R" ? 3 * 60 * 60 : 90 * 60
        return now >= start.addingTimeInterval(duration)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Space.md) {
                header
                if !event.sessions.isEmpty {
                    WeekendScheduleCard(event: event)
                }
                if event.completed {
                    RaceAnalysisGrid(event: event)
                }
                sessionPicker
                LoadableView(state: vm.state) {
                    await vm.load(year: event.year, round: event.round, session: selectedSession)
                } content: { response in
                    ResultsTable(response: response)
                }
                .frame(minHeight: 320)
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
        .navigationTitle(event.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: selectedSession) {
            await vm.load(year: event.year, round: event.round, session: selectedSession)
        }
    }

    private var header: some View {
        Card {
            HStack(spacing: Theme.Space.md) {
                Text(CountryFlag.flag(country: event.country))
                    .font(.system(size: 52))
                VStack(alignment: .leading, spacing: 4) {
                    Text(event.officialName ?? event.displayName)
                        .font(.headline)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text([event.location, event.country].compactMap { $0 }.joined(separator: ", "))
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    if let date = event.parsedDate {
                        Text(date.formatted(date: .long, time: .omitted))
                            .font(.caption)
                            .foregroundStyle(Theme.Palette.textTertiary)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    @ViewBuilder private var sessionPicker: some View {
        if resultSessions.count > 1 {
            Picker("Session", selection: $selectedSession) {
                ForEach(resultSessions, id: \.id) { s in
                    Text(s.label).tag(s.id)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: selectedSession) { _, _ in Haptics.selection() }
        }
    }
}

// MARK: - Results table

/// What a session's results actually contain, which decides how a row reads.
/// Mirrors the web's `ResultsVariant` in `ResultsTable.tsx`.
private enum SessionClassification {
    /// Finishing order: time or gap, places gained, points.
    case race
    /// Segment times — Q1/Q2/Q3, or SQ1/SQ2/SQ3 on a sprint weekend.
    case qualifying
    /// Practice, where nobody finishes anything and there is only ever a
    /// timesheet: best lap, gap to the fastest, laps run.
    case bestLap

    init(session: String) {
        switch session {
        case "Q", "SQ", "SS": self = .qualifying
        case let s where s.hasPrefix("FP"): self = .bestLap
        default: self = .race
        }
    }
}

private struct ResultsTable: View {
    let response: SessionResultsResponse
    private var classification: SessionClassification { SessionClassification(session: response.session) }

    var body: some View {
        if response.results.isEmpty {
            EmptyStateView(icon: "list.number", title: "No Results",
                           message: "Results aren't available for this session yet.")
                .frame(minHeight: 240)
        } else {
            VStack(spacing: Theme.Space.sm) {
                ForEach(response.results) { entry in
                    ResultRow(entry: entry, classification: classification,
                              winnerTimeMs: response.results.first?.timeMs)
                }
            }
        }
    }
}

private struct ResultRow: View {
    let entry: ResultEntry
    let classification: SessionClassification
    let winnerTimeMs: Int?

    private var accent: Color { .team(entry.teamColor) }
    private var isPodium: Bool { (entry.position ?? 99) <= 3 }

    var body: some View {
        HStack(spacing: Theme.Space.sm) {
            PositionBadge(text: entry.positionLabel, highlight: isPodium)
            TeamAccentBar(color: accent).frame(height: 40)

            DriverAvatar(url: entry.headshotUrl,
                         initials: entry.abbreviation ?? "?",
                         accent: accent, size: 40)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.fullName ?? entry.abbreviation ?? "Unknown")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .lineLimit(1)
                HStack(spacing: 4) {
                    TeamLogoView(url: entry.teamLogoUrl, size: 16)
                    Text(entry.teamName ?? "")
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 4)

            trailing
        }
        .padding(.vertical, 6)
        .padding(.horizontal, Theme.Space.sm)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
    }

    @ViewBuilder private var trailing: some View {
        VStack(alignment: .trailing, spacing: 2) {
            switch classification {
            case .qualifying:
                Text(entry.q3 ?? entry.q2 ?? entry.q1 ?? "–")
                    .font(.system(.subheadline, design: .monospaced).weight(.semibold))
                    .foregroundStyle(Theme.Palette.textPrimary)
            case .bestLap:
                Text(entry.bestLap ?? "–")
                    .font(.system(.subheadline, design: .monospaced).weight(.semibold))
                    .foregroundStyle(Theme.Palette.textPrimary)
                HStack(spacing: 6) {
                    if let gap = entry.bestLapGap {
                        Text(gap)
                            .font(.system(.caption2, design: .monospaced))
                            .foregroundStyle(Theme.Palette.textSecondary)
                    }
                    if let laps = lapsLabel {
                        Text(laps)
                            .font(.caption2)
                            .foregroundStyle(Theme.Palette.textTertiary)
                    }
                }
            case .race:
                Text(raceTime)
                    .font(.system(.subheadline, design: .monospaced))
                    .foregroundStyle(Theme.Palette.textPrimary)
                HStack(spacing: 6) {
                    if let delta = entry.gridDelta { GridDeltaTag(delta: delta) }
                    if !entry.pointsLabel.isEmpty {
                        Text("+\(entry.pointsLabel)")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.Palette.racingRedText)
                    }
                }
            }
        }
    }

    private var lapsLabel: String? {
        guard let laps = entry.lapsCompleted else { return nil }
        return laps == 1 ? "1 lap" : "\(laps) laps"
    }

    // Lives on ResultEntry (see Models/Formatters.swift) rather than here so it
    // is unit-testable and stays in step with the Android implementation.
    private var raceTime: String {
        entry.raceTimeLabel(winnerTimeMs: winnerTimeMs)
    }
}

@MainActor
final class RaceDetailViewModel: ObservableObject {
    @Published var state: Loadable<SessionResultsResponse> = .idle
    private var key: String?

    func load(year: Int, round: Int, session: String) async {
        let newKey = "\(year)-\(round)-\(session)"
        if key == newKey, case .loaded = state { return }
        state = .loading
        do {
            let response = try await APIClient.shared.results(year: year, round: round, session: session)
            state = .loaded(response)
            key = newKey
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
