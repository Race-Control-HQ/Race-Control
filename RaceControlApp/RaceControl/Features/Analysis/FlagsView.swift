import SwiftUI

/// Track flags and safety-car periods issued during a session: a chronological
/// list of collapsed periods (primary) with the raw race-control timeline
/// available underneath for detail.
struct FlagsView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = FlagsViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { data in
            if data.periods.isEmpty {
                EmptyStateView(icon: "flag.checkered", title: "No Flags",
                               message: "Clean race: no yellow, red or safety-car periods were recorded.")
            } else {
                content(data)
            }
        }
        .navigationTitle("Flags")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }

    private func content(_ data: FlagsResponse) -> some View {
        ScrollView {
            VStack(spacing: Theme.Space.sm) {
                Text("\(data.periods.count) flag period\(data.periods.count == 1 ? "" : "s") across \(data.totalLaps) laps")
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Theme.Space.md)
                    .padding(.top, Theme.Space.sm)

                VStack(spacing: Theme.Space.sm) {
                    ForEach(data.periods) { period in
                        FlagPeriodRow(period: period)
                    }
                }
                .padding(.horizontal, Theme.Space.md)

                if !data.events.isEmpty {
                    timeline(data.events)
                        .padding(.horizontal, Theme.Space.md)
                        .padding(.top, Theme.Space.sm)
                }
            }
            .padding(.bottom, Theme.Space.md)
        }
    }

    private func timeline(_ events: [FlagEvent]) -> some View {
        DisclosureGroup {
            VStack(spacing: Theme.Space.xs) {
                ForEach(events) { event in
                    FlagEventRow(event: event)
                }
            }
            .padding(.top, Theme.Space.sm)
        } label: {
            Text("RACE CONTROL TIMELINE (\(events.count))")
                .font(.caption.weight(.bold)).tracking(1)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .tint(Theme.Palette.textSecondary)
    }
}

private struct FlagPeriodRow: View {
    let period: FlagPeriod
    private var color: Color { FlagStyle.color(period.type) }

    var body: some View {
        HStack(spacing: Theme.Space.md) {
            Image(systemName: FlagStyle.icon(period.type))
                .font(.title3)
                .foregroundStyle(color)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(FlagStyle.label(period.type))
                    .font(.headline)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text(lapRangeText)
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                if let reason = period.reason, !reason.isEmpty {
                    Text(reason)
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textTertiary)
                        .lineLimit(2)
                }
            }
            Spacer()
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2)
                .fill(color)
                .frame(width: 4)
        }
        .accessibilityElement(children: .combine)
    }

    private var lapRangeText: String {
        period.startLap == period.endLap
            ? "Lap \(period.startLap)"
            : "Laps \(period.startLap)–\(period.endLap)"
    }
}

private struct FlagEventRow: View {
    let event: FlagEvent
    private var color: Color { FlagStyle.eventColor(event.flag) }

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.sm) {
            Circle().fill(color).frame(width: 8, height: 8).padding(.top, 5)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if let lap = event.lap {
                        Text("Lap \(lap)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.Palette.textPrimary)
                    }
                    if let timeText {
                        Text(timeText)
                            .font(.caption2)
                            .foregroundStyle(Theme.Palette.textTertiary)
                    }
                    if let code = event.driverCode {
                        Text(code)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.Palette.textSecondary)
                    }
                }
                Text(event.message ?? event.flag ?? event.category ?? "–")
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
            Spacer()
        }
        .padding(.vertical, 4)
    }

    private var timeText: String? { ISO8601.clockWithZone(event.time) }
}

@MainActor
final class FlagsViewModel: ObservableObject {
    @Published var state: Loadable<FlagsResponse> = .idle

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.flags(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
