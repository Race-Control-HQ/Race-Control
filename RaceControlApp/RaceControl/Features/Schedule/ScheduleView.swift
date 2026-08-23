import SwiftUI

struct ScheduleView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var vm = ScheduleViewModel()
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            LoadableView(state: vm.state) {
                await vm.load(year: appState.selectedYear)
            } content: { events in
                if events.isEmpty {
                    EmptyStateView(icon: "calendar", title: "No Races",
                                   message: "No schedule found for \(appState.selectedYear).")
                } else {
                    ScheduleList(events: events)
                        .refreshable { await vm.load(year: appState.selectedYear, force: true) }
                }
            }
            .navigationTitle("Season \(String(appState.selectedYear))")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
                ToolbarItem(placement: .topBarTrailing) { SeasonPicker() }
            }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .background(Theme.Palette.background)
        }
        .task(id: appState.selectedYear) { await vm.load(year: appState.selectedYear) }
    }
}

private struct ScheduleList: View {
    let events: [RaceEvent]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: Theme.Space.md) {
                if let next = events.first(where: { !$0.completed }) {
                    UpNextBanner(event: next)
                }
                ForEach(events) { event in
                    NavigationLink(value: event) {
                        RaceRow(event: event)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
        .navigationDestination(for: RaceEvent.self) { event in
            RaceDetailView(event: event)
        }
    }
}

private struct UpNextBanner: View {
    let event: RaceEvent
    var body: some View {
        NavigationLink(value: event) {
            HStack(spacing: Theme.Space.md) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("UP NEXT")
                        .font(.caption2.weight(.bold)).tracking(1)
                        .foregroundStyle(.white.opacity(0.85))
                    Text(event.displayName)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                    if let date = event.parsedDate {
                        Text(date.formatted(date: .abbreviated, time: .omitted))
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }
                Spacer()
                Text(CountryFlag.flag(country: event.country))
                    .font(.system(size: 44))
            }
            .padding(Theme.Space.md)
            .frame(maxWidth: .infinity)
            .background(
                LinearGradient(colors: [Theme.Palette.racingRed, Theme.Palette.racingRedDim],
                               startPoint: .topLeading, endPoint: .bottomTrailing),
                in: RoundedRectangle(cornerRadius: Theme.Radius.lg)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct RaceRow: View {
    let event: RaceEvent

    var body: some View {
        Card {
            HStack(spacing: Theme.Space.md) {
                VStack(spacing: 0) {
                    Text("R\(event.round)")
                        .font(.system(.caption, design: .rounded).weight(.bold))
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Text(CountryFlag.flag(country: event.country))
                        .font(.system(size: 30))
                }
                .frame(width: 44)

                VStack(alignment: .leading, spacing: 3) {
                    Text(event.displayName)
                        .font(.headline)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .lineLimit(1)
                    Text(event.location ?? event.country ?? "")
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .lineLimit(1)
                    HStack(spacing: Theme.Space.sm) {
                        if let date = event.parsedDate {
                            Text(date.formatted(date: .abbreviated, time: .omitted))
                                .font(.caption)
                                .foregroundStyle(Theme.Palette.textTertiary)
                        }
                        if event.isSprintWeekend {
                            Text("SPRINT")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(Theme.Palette.warning)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Theme.Palette.warning.opacity(0.15), in: Capsule())
                        }
                    }
                }
                Spacer()
                if event.completed {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Theme.Palette.positive.opacity(0.8))
                }
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.Palette.textTertiary)
            }
        }
    }
}

@MainActor
final class ScheduleViewModel: ObservableObject {
    @Published var state: Loadable<[RaceEvent]> = .idle
    private var loadedYear: Int?

    func load(year: Int, force: Bool = false) async {
        if !force, loadedYear == year, case .loaded = state { return }
        if case .idle = state { state = .loading } else if force { } else { state = .loading }
        do {
            let events = try await APIClient.shared.schedule(year: year)
            state = .loaded(events)
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
