import SwiftUI

/// The complete, unfiltered race-control message log for a session: flags,
/// safety-car, DRS enable/disable, car events and "other" messages (stewards'
/// investigations, penalties, reprimands) in chronological order. Complements
/// `FlagsView`, which only surfaces the flag/safety-car subset.
struct RaceControlView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = RaceControlViewModel()
    @State private var filter: RaceControlFilter = .all

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { data in
            if data.messages.isEmpty {
                EmptyStateView(icon: "list.bullet.clipboard", title: "No Messages",
                               message: "No race-control messages were recorded for this session.")
            } else {
                content(data)
            }
        }
        .navigationTitle("Race Control")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }

    private func content(_ data: RaceControlResponse) -> some View {
        let filtered = filter.apply(to: data.messages)
        return VStack(spacing: 0) {
            Picker("Filter", selection: $filter) {
                ForEach(RaceControlFilter.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(Theme.Space.md)
            .onChange(of: filter) { _, _ in Haptics.selection() }

            ScrollView {
                VStack(spacing: Theme.Space.sm) {
                    Text("\(filtered.count) message\(filtered.count == 1 ? "" : "s") across \(data.totalLaps) laps")
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Theme.Space.md)

                    if filtered.isEmpty {
                        EmptyStateView(icon: "line.3.horizontal.decrease.circle", title: "No Matches",
                                       message: "No messages match this filter.")
                            .padding(.top, Theme.Space.lg)
                    } else {
                        VStack(spacing: Theme.Space.xs) {
                            ForEach(filtered) { message in
                                RaceControlRow(message: message)
                            }
                        }
                        .padding(.horizontal, Theme.Space.md)
                    }
                }
                .padding(.bottom, Theme.Space.md)
            }
        }
    }
}

private enum RaceControlFilter: String, CaseIterable, Hashable {
    case all, flags, safetyCar, drs, incidents

    var label: String {
        switch self {
        case .all: return "All"
        case .flags: return "Flags"
        case .safetyCar: return "Safety Car"
        case .drs: return "DRS"
        case .incidents: return "Incidents"
        }
    }

    func apply(to messages: [RaceControlMessage]) -> [RaceControlMessage] {
        switch self {
        case .all: return messages
        case .flags: return messages.filter { $0.category == "Flag" }
        case .safetyCar: return messages.filter { $0.category == "SafetyCar" }
        case .drs: return messages.filter { $0.category == "Drs" }
        case .incidents: return messages.filter { $0.category == "CarEvent" || $0.category == "Other" }
        }
    }
}

private struct RaceControlRow: View {
    let message: RaceControlMessage
    private var color: Color { RaceControlStyle.color(message.category, flag: message.flag) }

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.md) {
            Image(systemName: RaceControlStyle.icon(message.category))
                .font(.subheadline)
                .foregroundStyle(color)
                .frame(width: 24, height: 24)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if let lap = message.lap {
                        Text("Lap \(lap)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.Palette.textPrimary)
                    }
                    if let timeText {
                        Text(timeText)
                            .font(.caption2)
                            .foregroundStyle(Theme.Palette.textTertiary)
                    }
                    if let code = message.driverCode {
                        Text(code)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.Palette.textPrimary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1)
                            .background(Theme.Palette.surfaceElevated, in: Capsule())
                    }
                    Spacer()
                    Text(RaceControlStyle.label(message.category))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(color)
                }
                Text(message.message ?? message.flag ?? "–")
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
        }
        .padding(Theme.Space.sm)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
        .accessibilityElement(children: .combine)
    }

    private var timeText: String? { ISO8601.clockWithZone(message.time) }
}

@MainActor
final class RaceControlViewModel: ObservableObject {
    @Published var state: Loadable<RaceControlResponse> = .idle

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.raceControl(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
