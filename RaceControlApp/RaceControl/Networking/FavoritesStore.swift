import Foundation
import SwiftUI

/// Persists the user's favourite drivers and teams and publishes changes so any
/// list can pin them to the top and show a filled star.
@MainActor
final class FavoritesStore: ObservableObject {
    static let shared = FavoritesStore()

    @Published private(set) var driverIds: Set<String>
    @Published private(set) var teamIds: Set<String>

    private let driverKey = "favorite_drivers"
    private let teamKey = "favorite_teams"

    init() {
        let defaults = UserDefaults.standard
        driverIds = Set(defaults.stringArray(forKey: driverKey) ?? [])
        teamIds = Set(defaults.stringArray(forKey: teamKey) ?? [])
    }

    func isFavoriteDriver(_ id: String?) -> Bool {
        guard let id else { return false }
        return driverIds.contains(id)
    }
    func isFavoriteTeam(_ id: String?) -> Bool {
        guard let id else { return false }
        return teamIds.contains(id)
    }

    func toggleDriver(_ id: String) {
        let adding = !driverIds.contains(id)
        if adding { driverIds.insert(id) } else { driverIds.remove(id) }
        UserDefaults.standard.set(Array(driverIds), forKey: driverKey)
        adding ? Haptics.success() : Haptics.impact(.light)
    }
    func toggleTeam(_ id: String) {
        let adding = !teamIds.contains(id)
        if adding { teamIds.insert(id) } else { teamIds.remove(id) }
        UserDefaults.standard.set(Array(teamIds), forKey: teamKey)
        adding ? Haptics.success() : Haptics.impact(.light)
    }
}

/// A reusable star toggle button.
struct FavoriteStar: View {
    let isOn: Bool
    let action: () -> Void
    var size: CGFloat = 22

    var body: some View {
        Button(action: action) {
            Image(systemName: isOn ? "star.fill" : "star")
                .font(.system(size: size))
                .foregroundStyle(isOn ? Color(hex: "FFD700") : Theme.Palette.textTertiary)
                .frame(width: Theme.minTouch, height: Theme.minTouch)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isOn ? "Remove favourite" : "Add favourite")
    }
}
