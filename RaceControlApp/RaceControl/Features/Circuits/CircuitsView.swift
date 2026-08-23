import SwiftUI

struct CircuitsView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var vm = CircuitsViewModel()

    var body: some View {
        NavigationStack {
            LoadableView(state: vm.state) {
                await vm.load(year: appState.selectedYear)
            } content: { entries in
                if entries.isEmpty {
                    EmptyStateView(icon: "map", title: "No Circuits",
                                   message: "No circuit data for \(appState.selectedYear).")
                } else {
                    ScrollView {
                        LazyVStack(spacing: Theme.Space.sm) {
                            ForEach(entries) { entry in
                                CircuitRow(entry: entry, year: appState.selectedYear)
                            }
                        }
                        .padding(Theme.Space.md)
                    }
                    .background(Theme.Palette.background)
                    .refreshable { await vm.load(year: appState.selectedYear, force: true) }
                }
            }
            .navigationTitle("Circuits")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { SeasonPicker() } }
            .background(Theme.Palette.background)
        }
        .task(id: appState.selectedYear) { await vm.load(year: appState.selectedYear) }
    }
}

/// A circuit paired with its matching calendar event (if any).
struct CircuitEntry: Identifiable, Hashable {
    let circuit: Circuit
    let event: RaceEvent?
    var id: String { circuit.id }
    var isRaced: Bool { event?.completed == true }
}

private struct CircuitRow: View {
    let entry: CircuitEntry
    let year: Int

    private var circuit: Circuit { entry.circuit }

    var body: some View {
        Group {
            if entry.isRaced, let round = entry.event?.round {
                NavigationLink {
                    CircuitDetailView(year: year, round: round, circuit: circuit, event: entry.event)
                } label: { content }
                .buttonStyle(.plain)
            } else {
                content
            }
        }
    }

    private var content: some View {
        HStack(spacing: Theme.Space.md) {
            // Round number + flag
            VStack(spacing: 2) {
                if let round = entry.event?.round {
                    Text("R\(round)")
                        .font(.system(.caption, design: .rounded).weight(.bold))
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
                Text(CountryFlag.flag(country: circuit.country))
                    .font(.system(size: 30))
            }
            .frame(width: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text(circuit.name ?? "Unknown Circuit")
                    .font(.headline)
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .lineLimit(2)
                Text([circuit.locality, circuit.country].compactMap { $0 }.joined(separator: ", "))
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .lineLimit(1)
                statusBadge
            }
            Spacer(minLength: 4)
            if entry.isRaced {
                Image(systemName: "map.fill").foregroundStyle(Theme.Palette.info)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.Palette.textTertiary)
            }
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(Theme.Palette.stroke, lineWidth: 1))
        .opacity(entry.event == nil ? 0.7 : 1)
    }

    @ViewBuilder private var statusBadge: some View {
        if let event = entry.event {
            if event.completed {
                badge(text: "Raced", icon: "checkmark.circle.fill",
                      color: Theme.Palette.positive)
            } else {
                let dateText = event.parsedDate.map {
                    $0.formatted(date: .abbreviated, time: .omitted)
                }
                badge(text: dateText.map { "Upcoming · \($0)" } ?? "Upcoming",
                      icon: "clock.fill", color: Theme.Palette.warning)
            }
        } else {
            badge(text: "Not on \(year) calendar", icon: "calendar.badge.minus",
                  color: Theme.Palette.textTertiary)
        }
    }

    private func badge(text: String, icon: String, color: Color) -> some View {
        Label(text, systemImage: icon)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.15), in: Capsule())
    }
}

@MainActor
final class CircuitsViewModel: ObservableObject {
    // Circuits paired with their calendar event, sorted into race-calendar order.
    @Published var state: Loadable<[CircuitEntry]> = .idle
    private var loadedYear: Int?

    func load(year: Int, force: Bool = false) async {
        if !force, loadedYear == year, case .loaded = state { return }
        state = .loading
        do {
            async let circuitsReq = APIClient.shared.circuits(year: year)
            async let scheduleReq = APIClient.shared.schedule(year: year)
            let circuits = try await circuitsReq
            let schedule = try await scheduleReq

            let entries = circuits.map { CircuitEntry(circuit: $0, event: Self.match($0, in: schedule)) }

            // Sort: calendar events by round first, unmatched circuits last (by name).
            let sorted = entries.sorted { a, b in
                switch (a.event?.round, b.event?.round) {
                case let (ra?, rb?): return ra < rb
                case (nil, _?): return false
                case (_?, nil): return true
                default: return (a.circuit.name ?? "") < (b.circuit.name ?? "")
                }
            }
            state = .loaded(sorted)
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    /// Match a circuit to a calendar event by city, falling back to a unique country.
    private static func match(_ circuit: Circuit, in schedule: [RaceEvent]) -> RaceEvent? {
        if let loc = circuit.locality?.lowercased(),
           let byCity = schedule.first(where: { $0.location?.lowercased() == loc }) {
            return byCity
        }
        let country = circuit.country?.lowercased()
        let sameCountry = schedule.filter { $0.country?.lowercased() == country }
        return sameCountry.count == 1 ? sameCountry.first : nil
    }
}
