import SwiftUI

/// Qualifying deep-dive: Q1/Q2/Q3 times with gap-to-pole for each driver.
struct QualifyingView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = QualifyingViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { response in
            if response.results.isEmpty {
                EmptyStateView(icon: "stopwatch", title: "No Qualifying",
                               message: "Qualifying data isn't available for this event.")
            } else {
                content(response)
            }
        }
        .navigationTitle("Qualifying")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }

    private func content(_ response: SessionResultsResponse) -> some View {
        let poleSeconds = vm.poleTime(response.results)
        return ScrollView {
            VStack(spacing: Theme.Space.sm) {
                headerRow
                ForEach(response.results) { entry in
                    QualiRow(entry: entry, poleSeconds: poleSeconds)
                }
            }
            .padding(Theme.Space.md)
        }
    }

    private var headerRow: some View {
        HStack(spacing: Theme.Space.sm) {
            Text("").frame(width: 34)
            Text("Driver").font(.caption.weight(.semibold)).foregroundStyle(Theme.Palette.textSecondary)
            Spacer()
            Text("Best / Gap").font(.caption.weight(.semibold)).foregroundStyle(Theme.Palette.textSecondary)
        }
        .padding(.horizontal, Theme.Space.sm)
    }
}

private struct QualiRow: View {
    let entry: ResultEntry
    let poleSeconds: Double?
    private var accent: Color { .team(entry.teamColor) }
    private var best: String? { entry.q3 ?? entry.q2 ?? entry.q1 }

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: Theme.Space.sm) {
                PositionBadge(text: entry.positionLabel, highlight: (entry.position ?? 99) <= 3)
                TeamAccentBar(color: accent).frame(height: 34)
                TeamLogoView(url: entry.teamLogoUrl, size: 22)
                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.fullName ?? entry.abbreviation ?? "")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textPrimary).lineLimit(1)
                    Text(entry.teamName ?? "").font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary).lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text(best ?? "–")
                        .font(.system(.subheadline, design: .monospaced).weight(.semibold))
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text(gapLabel)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(entry.position == 1 ? Color(hex: "FFD700") : Theme.Palette.info)
                }
            }
            // Q1/Q2/Q3 breakdown
            HStack(spacing: Theme.Space.sm) {
                segment("Q1", entry.q1, entry.q1Gap)
                segment("Q2", entry.q2, entry.q2Gap)
                segment("Q3", entry.q3, entry.q3Gap)
            }
        }
        .padding(Theme.Space.sm)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private func segment(_ label: String, _ time: String?, _ gap: String?) -> some View {
        VStack(spacing: 1) {
            Text(label).font(.caption2.weight(.bold)).foregroundStyle(Theme.Palette.textTertiary)
            Text(time ?? "–")
                .font(.system(.caption2, design: .monospaced))
                .foregroundStyle(time == nil ? Theme.Palette.textTertiary : Theme.Palette.textSecondary)
            if let gap {
                Text(gap)
                    .font(.caption2)
                    .foregroundStyle(Theme.Palette.textTertiary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
        .background(Theme.Palette.surfaceElevated, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
    }

    private var gapLabel: String {
        guard entry.position != 1 else { return "POLE" }
        guard let pole = poleSeconds, let mine = LapFormat.lapToSeconds(best) else { return "" }
        let gap = mine - pole
        return gap > 0 ? String(format: "+%.3f", gap) : ""
    }
}

@MainActor
final class QualifyingViewModel: ObservableObject {
    @Published var state: Loadable<SessionResultsResponse> = .idle

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.results(year: year, round: round, session: "Q"))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func poleTime(_ results: [ResultEntry]) -> Double? {
        results.compactMap { LapFormat.lapToSeconds($0.q3 ?? $0.q2 ?? $0.q1) }.min()
    }
}
