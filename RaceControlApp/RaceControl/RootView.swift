import SwiftUI

/// Root tab bar. Five top-level sections per HIG (≤5 tabs, always visible).
struct RootView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        TabView {
            ScheduleView()
                .tabItem { Label("Races", systemImage: "flag.checkered") }

            DriversView()
                .tabItem { Label("Drivers", systemImage: "person.2.fill") }

            TeamsView()
                .tabItem { Label("Teams", systemImage: "car.2.fill") }

            StandingsView()
                .tabItem { Label("Standings", systemImage: "trophy.fill") }

            CircuitsView()
                .tabItem { Label("Circuits", systemImage: "map.fill") }
        }
        .task {
            await appState.loadSeasons()
            // Keep the on-device reminder window fresh for the current season.
            await NotificationManager.shared.refreshIfEnabled()
        }
    }
}
