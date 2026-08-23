import SwiftUI

@main
struct RaceControlApp: App {
    @StateObject private var appState = AppState()
    @StateObject private var favorites = FavoritesStore.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .environmentObject(favorites)
                .preferredColorScheme(.dark)         // dark-first, OLED-tuned
                .tint(Theme.Palette.racingRed)
        }
    }
}

/// App-wide state: the currently selected season, shared across every tab.
@MainActor
final class AppState: ObservableObject {
    @Published var seasons: [Int] = []
    @Published var selectedYear: Int = Calendar.current.component(.year, from: Date())
    @Published var loadingSeasons = false

    private let api = APIClient.shared

    func loadSeasons() async {
        guard seasons.isEmpty else { return }
        loadingSeasons = true
        defer { loadingSeasons = false }
        do {
            let years = try await api.seasons()
            seasons = years
            if let latest = years.first { selectedYear = latest }
        } catch {
            // Fall back to a sensible static range if the server is unreachable.
            let current = Calendar.current.component(.year, from: Date())
            seasons = Array(stride(from: current, through: 2018, by: -1))
            selectedYear = current
        }
    }
}
