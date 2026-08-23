import SwiftUI

/// Season reliability: finish rate and DNF breakdown (mechanical / accident /
/// disqualified / other) per driver and per team. Driver and team lists
/// share one sort control so the two rankings stay comparable, and each row
/// opens a detail sheet with the full breakdown.
struct ReliabilityView: View {
    let year: Int
    @StateObject private var vm = ReliabilityViewModel()
    @State private var mode: Mode = .drivers
    @State private var sort: SortField = .finishRate
    @State private var selected: ReliabilityEntry?

    enum Mode: String, CaseIterable { case drivers = "Drivers", teams = "Teams" }

    enum SortField: String, CaseIterable {
        case finishRate = "Finish Rate", mechanical = "Mechanical", accident = "Accident", starts = "Starts"

        func value(_ e: ReliabilityEntry) -> Double {
            switch self {
            case .finishRate: e.finishRate
            case .mechanical: Double(e.mechanical)
            case .accident: Double(e.accident)
            case .starts: Double(e.starts)
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Picker("Mode", selection: $mode) {
                    ForEach(Mode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)

                Menu {
                    Picker("Sort by", selection: $sort) {
                        ForEach(SortField.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                } label: {
                    Label(sort.rawValue, systemImage: "arrow.up.arrow.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .lineLimit(1)
                }
                .frame(minWidth: 100)
            }
            .padding(.horizontal, Theme.Space.md)
            .padding(.bottom, Theme.Space.sm)

            LoadableView(state: vm.state) {
                await vm.load(year: year)
            } content: { data in
                ScrollView {
                    VStack(spacing: Theme.Space.sm) {
                        legend
                        let entries: [ReliabilityEntry] = mode == .drivers
                            ? data.drivers.map(ReliabilityEntry.driver)
                            : data.teams.map(ReliabilityEntry.team)
                        ForEach(entries.sorted { sort.value($0) > sort.value($1) }) { entry in
                            Button { selected = entry } label: {
                                ReliabilityRow(entry: entry)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(Theme.Space.md)
                }
            }
        }
        .background(Theme.Palette.background)
        .task(id: year) { await vm.load(year: year) }
        .sheet(item: $selected) { entry in
            ReliabilityDetailSheet(entry: entry)
                .presentationDetents([.medium])
        }
    }

    private var legend: some View {
        HStack(spacing: Theme.Space.md) {
            legendItem("Finished", Theme.Palette.positive)
            legendItem("Mechanical", Theme.Palette.warning)
            legendItem("Accident", Theme.Palette.negative)
            legendItem("Other", Theme.Palette.textTertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func legendItem(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 10, height: 10)
            Text(label).font(.caption2).foregroundStyle(Theme.Palette.textSecondary)
        }
    }
}

/// Common shape for a driver or team reliability row, so sorting and
/// row/detail rendering don't need to branch on which one it is.
struct ReliabilityEntry: Identifiable {
    let id: String
    let title: String
    let finished: Int
    let mechanical: Int
    let accident: Int
    let disqualified: Int
    let other: Int
    let starts: Int
    let finishRate: Double
    let logoUrl: String?

    static func driver(_ d: ReliabilityDriver) -> ReliabilityEntry {
        ReliabilityEntry(id: "driver-\(d.driverId)", title: d.name, finished: d.finished,
                          mechanical: d.mechanical, accident: d.accident, disqualified: d.disqualified,
                          other: d.other, starts: d.starts, finishRate: d.finishRate, logoUrl: nil)
    }
    static func team(_ t: ReliabilityTeam) -> ReliabilityEntry {
        ReliabilityEntry(id: "team-\(t.teamId)", title: t.teamName ?? t.teamId, finished: t.finished,
                          mechanical: t.mechanical, accident: t.accident, disqualified: t.disqualified,
                          other: t.other, starts: t.starts, finishRate: t.finishRate, logoUrl: t.teamLogoUrl)
    }
}

private struct ReliabilityRow: View {
    let entry: ReliabilityEntry

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            HStack {
                TeamLogoView(url: entry.logoUrl, size: 20)
                Text(entry.title)
                    .font(.headline).foregroundStyle(Theme.Palette.textPrimary).lineLimit(1)
                Spacer()
                Text("\(Int(entry.finishRate))%")
                    .font(.system(.subheadline, design: .rounded).weight(.bold))
                    .foregroundStyle(finishColor)
                Text("finished").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            }
            // Stacked reliability bar
            GeometryReader { geo in
                HStack(spacing: 1) {
                    segment(entry.finished, Theme.Palette.positive, geo.size.width)
                    segment(entry.mechanical, Theme.Palette.warning, geo.size.width)
                    segment(entry.accident, Theme.Palette.negative, geo.size.width)
                    segment(entry.disqualified + entry.other, Theme.Palette.textTertiary, geo.size.width)
                }
            }
            .frame(height: 10)
            .clipShape(Capsule())

            HStack(spacing: Theme.Space.md) {
                if entry.mechanical > 0 { tag("\(entry.mechanical) mech", Theme.Palette.warning) }
                if entry.accident > 0 { tag("\(entry.accident) crash", Theme.Palette.negative) }
                if entry.disqualified > 0 { tag("\(entry.disqualified) DSQ", Theme.Palette.racingRedText) }
                Spacer()
                Text("\(entry.starts) starts").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            }
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(Theme.Palette.stroke, lineWidth: 1))
    }

    private func segment(_ count: Int, _ color: Color, _ width: CGFloat) -> some View {
        Rectangle().fill(color)
            .frame(width: entry.starts > 0 ? max(width * CGFloat(count) / CGFloat(entry.starts), count > 0 ? 3 : 0) : 0)
    }

    private func tag(_ text: String, _ color: Color) -> some View {
        Text(text).font(.caption2.weight(.semibold)).foregroundStyle(color)
    }

    private var finishColor: Color {
        entry.finishRate >= 90 ? Theme.Palette.positive : entry.finishRate >= 75 ? Theme.Palette.warning : Theme.Palette.negative
    }
}

private struct ReliabilityDetailSheet: View {
    let entry: ReliabilityEntry

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.md) {
            HStack {
                TeamLogoView(url: entry.logoUrl, size: 36)
                Text(entry.title).font(.title3.weight(.bold)).foregroundStyle(Theme.Palette.textPrimary)
            }
            Text("\(Int(entry.finishRate))% finish rate over \(entry.starts) starts")
                .font(.subheadline).foregroundStyle(Theme.Palette.textSecondary)

            VStack(spacing: Theme.Space.sm) {
                detailRow("Finished", entry.finished, Theme.Palette.positive)
                detailRow("Mechanical", entry.mechanical, Theme.Palette.warning)
                detailRow("Accident", entry.accident, Theme.Palette.negative)
                detailRow("Disqualified", entry.disqualified, Theme.Palette.racingRedText)
                detailRow("Other", entry.other, Theme.Palette.textTertiary)
            }
            Spacer()
        }
        .padding(Theme.Space.lg)
    }

    private func detailRow(_ label: String, _ count: Int, _ color: Color) -> some View {
        HStack {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.subheadline).foregroundStyle(Theme.Palette.textSecondary)
            Spacer()
            Text("\(count)").font(.subheadline.weight(.bold)).foregroundStyle(Theme.Palette.textPrimary)
        }
    }
}

@MainActor
final class ReliabilityViewModel: ObservableObject {
    @Published var state: Loadable<ReliabilityResponse> = .idle
    private var loadedYear: Int?

    func load(year: Int) async {
        if loadedYear == year, case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.reliability(year: year))
            loadedYear = year
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
