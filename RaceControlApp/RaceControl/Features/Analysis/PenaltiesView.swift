import SwiftUI

/// Stewards' penalty decisions for a session (time penalties, drive-throughs,
/// grid drops, reprimands, disqualifications), parsed from the race-control log.
struct PenaltiesView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = PenaltiesViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { data in
            if data.penalties.isEmpty {
                EmptyStateView(icon: "checkmark.seal", title: "No Penalties",
                               message: "No penalties were issued in this session.")
            } else {
                content(data)
            }
        }
        .navigationTitle("Penalties")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }

    private func content(_ data: PenaltiesResponse) -> some View {
        ScrollView {
            VStack(spacing: Theme.Space.sm) {
                Text("\(data.penalties.count) penalt\(data.penalties.count == 1 ? "y" : "ies") issued")
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Theme.Space.md)
                    .padding(.top, Theme.Space.sm)

                VStack(spacing: Theme.Space.xs) {
                    ForEach(data.penalties) { penalty in
                        PenaltyRow(penalty: penalty, year: year, driversById: vm.driversById)
                    }
                }
                .padding(.horizontal, Theme.Space.md)
            }
            .padding(.bottom, Theme.Space.md)
        }
    }
}

/// Icon/colour mapping for `Penalty.type`.
private enum PenaltyStyle {
    static func color(_ type: String) -> Color {
        switch type {
        case "Time Penalty": return Theme.Palette.flagYellow
        case "Stop & Go Penalty", "Drive Through Penalty": return Theme.Palette.warning
        case "Grid Penalty": return Theme.Palette.info
        case "Reprimand": return Theme.Palette.textSecondary
        case "Disqualification": return Theme.Palette.negative
        default: return Theme.Palette.textTertiary
        }
    }

    static func icon(_ type: String) -> String {
        switch type {
        case "Time Penalty": return "clock.fill"
        case "Stop & Go Penalty": return "octagon.fill"
        case "Drive Through Penalty": return "arrow.forward.circle.fill"
        case "Grid Penalty": return "arrow.down.circle.fill"
        case "Reprimand": return "exclamationmark.bubble.fill"
        case "Disqualification": return "xmark.seal.fill"
        default: return "info.circle.fill"
        }
    }
}

private struct PenaltyRow: View {
    let penalty: Penalty
    let year: Int
    let driversById: [String: Driver]
    private var color: Color { PenaltyStyle.color(penalty.type) }

    private var matchedDriver: Driver? {
        guard let id = penalty.driverId else { return nil }
        return driversById[id]
    }

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.md) {
            Image(systemName: PenaltyStyle.icon(penalty.type))
                .font(.subheadline)
                .foregroundStyle(color)
                .frame(width: 24, height: 24)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if let lap = penalty.lap {
                        Text("Lap \(lap)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.Palette.textPrimary)
                    }
                    if let timeText {
                        Text(timeText)
                            .font(.caption2)
                            .foregroundStyle(Theme.Palette.textTertiary)
                    }
                    if let code = penalty.driverCode {
                        codeLabel(code)
                    }
                    Spacer()
                    Text(penalty.value.map { "\(penalty.type) (\($0))" } ?? penalty.type)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(color)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(color.opacity(0.15), in: Capsule())
                }
                if let driverName = penalty.driverName {
                    HStack(spacing: 4) {
                        TeamLogoView(url: penalty.teamLogoUrl, size: 16)
                        Text(driverName)
                            .font(.caption)
                            .foregroundStyle(Theme.Palette.textSecondary)
                    }
                }
                Text(penalty.reason ?? penalty.message ?? "–")
                    .font(.subheadline)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
        }
        .padding(Theme.Space.sm)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
        .accessibilityElement(children: .combine)
    }

    private var timeText: String? { ISO8601.clockWithZone(penalty.time) }

    @ViewBuilder
    private func codeLabel(_ code: String) -> some View {
        if let matchedDriver {
            NavigationLink {
                DriverDetailView(year: year, driver: matchedDriver)
            } label: {
                Text(code)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1)
                    .background(Theme.Palette.surfaceElevated, in: Capsule())
            }
        } else {
            Text(code)
                .font(.caption2.weight(.bold))
                .foregroundStyle(Theme.Palette.textPrimary)
                .padding(.horizontal, 6)
                .padding(.vertical, 1)
                .background(Theme.Palette.surfaceElevated, in: Capsule())
        }
    }
}

@MainActor
final class PenaltiesViewModel: ObservableObject {
    @Published var state: Loadable<PenaltiesResponse> = .idle
    @Published var driversById: [String: Driver] = [:]

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.penalties(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
        // Secondary lookup for driver-detail navigation; failure shouldn't block the screen.
        if let drivers = try? await APIClient.shared.drivers(year: year) {
            driversById = Dictionary(uniqueKeysWithValues: drivers.map { ($0.driverId, $0) })
        }
    }
}
