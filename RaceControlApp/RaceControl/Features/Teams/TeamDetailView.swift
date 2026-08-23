import SwiftUI

struct TeamDetailView: View {
    let year: Int
    let team: Team
    @EnvironmentObject private var favorites: FavoritesStore
    private var accent: Color { .team(team.teamColor) }

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Space.md) {
                header
                Card {
                    HStack {
                        StatCell(value: team.positionInt.map(String.init) ?? "–", label: "Position")
                        Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                        StatCell(value: team.pointsString, label: "Points", accent: accent)
                        Divider().frame(height: 36).overlay(Theme.Palette.stroke)
                        StatCell(value: team.wins?.numberLabel ?? "0", label: "Wins")
                    }
                }
                driversSection
            }
            .padding(Theme.Space.md)
        }
        .background(Theme.Palette.background)
        .navigationTitle(team.teamName ?? "Team")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                FavoriteStar(isOn: favorites.isFavoriteTeam(team.teamId),
                             action: { if let id = team.teamId { favorites.toggleTeam(id) } }, size: 18)
            }
        }
    }

    private var header: some View {
        VStack(spacing: Theme.Space.sm) {
            if team.teamLogoUrl != nil {
                TeamLogoView(url: team.teamLogoUrl, size: 64)
            } else {
                Image(systemName: "car.side.fill")
                    .font(.system(size: 54))
                    .foregroundStyle(accent)
            }
            Text(team.teamName ?? "")
                .font(.title.weight(.bold))
                .foregroundStyle(Theme.Palette.textPrimary)
                .multilineTextAlignment(.center)
            Text("\(CountryFlag.flag(country: team.nationality)) \(team.nationality ?? "")")
                .font(.subheadline)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Theme.Space.lg)
        .background(
            LinearGradient(colors: [accent.opacity(0.30), Theme.Palette.surface],
                           startPoint: .top, endPoint: .bottom),
            in: RoundedRectangle(cornerRadius: Theme.Radius.lg)
        )
    }

    @ViewBuilder private var driversSection: some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            Text("DRIVERS")
                .font(.caption.weight(.bold)).tracking(1)
                .foregroundStyle(Theme.Palette.textSecondary)
            PointsBreakdownCard(drivers: team.drivers, accent: accent)
            ForEach(team.drivers) { d in
                HStack(spacing: Theme.Space.md) {
                    DriverAvatar(url: d.headshotUrl, initials: d.code ?? "?", accent: accent, size: 52)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(d.name ?? "")
                            .font(.headline)
                            .foregroundStyle(Theme.Palette.textPrimary)
                        if let num = d.number?.stringValue {
                            Text("#\(num)")
                                .font(.subheadline)
                                .foregroundStyle(accent)
                        }
                    }
                    Spacer()
                    Text("\(d.pointsLabel) pts")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textTertiary)
                }
                .padding(Theme.Space.md)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            }
        }
    }
}

/// Who's actually carried the team, at a glance: a driver list alone
/// doesn't show whether points are split evenly or one driver is doing
/// most of the scoring.
private struct PointsBreakdownCard: View {
    let drivers: [TeamDriver]
    let accent: Color

    private var total: Double { drivers.reduce(0) { $0 + $1.pointsValue } }

    var body: some View {
        if total > 0 {
            Card {
                VStack(alignment: .leading, spacing: Theme.Space.sm) {
                    Text("POINTS BREAKDOWN")
                        .font(.caption2.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    GeometryReader { geo in
                        HStack(spacing: 2) {
                            ForEach(Array(drivers.enumerated()), id: \.element.id) { index, d in
                                let pct = d.pointsValue / total
                                if pct > 0 {
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(accent.opacity(index == 0 ? 1 : 0.5))
                                        .frame(width: max(0, geo.size.width * pct))
                                }
                            }
                        }
                    }
                    .frame(height: 10)
                    HStack {
                        ForEach(drivers) { d in
                            let pct = total > 0 ? (d.pointsValue / total) * 100 : 0
                            Text("\(d.code ?? d.name ?? "–"): \(d.pointsLabel) (\(Int(pct))%)")
                                .font(.caption)
                                .foregroundStyle(Theme.Palette.textSecondary)
                        }
                        Spacer()
                    }
                }
            }
        }
    }
}
