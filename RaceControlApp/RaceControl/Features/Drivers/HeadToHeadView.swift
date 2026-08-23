import SwiftUI
import Charts

/// Head-to-head: pick two drivers and compare their season, points, wins,
/// podiums, poles, best finish, DNFs, and direct qualifying/race records.
struct HeadToHeadView: View {
    let year: Int
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var typeSize
    @StateObject private var vm = HeadToHeadViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Theme.Space.md) {
                    slots
                    driverPicker
                    results
                }
                .padding(Theme.Space.md)
            }
            .background(Theme.Palette.background)
            .navigationTitle("Head to Head")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await vm.loadDrivers(year: year) }
        }
    }

    // MARK: Selected slots
    private var slots: some View {
        HStack(spacing: Theme.Space.sm) {
            slot(vm.driver1, accent: Theme.Palette.racingRed, placeholder: "Driver 1")
            Text("VS").font(.caption.weight(.black)).foregroundStyle(Theme.Palette.textTertiary)
            slot(vm.driver2, accent: Theme.Palette.info, placeholder: "Driver 2")
        }
    }

    private func slot(_ driver: Driver?, accent: Color, placeholder: String) -> some View {
        VStack(spacing: 4) {
            DriverAvatar(url: driver?.headshotUrl, initials: driver?.code ?? "?",
                         accent: accent, size: 56)
            Text(driver?.code ?? placeholder)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.Palette.textPrimary)
            HStack(spacing: 3) {
                TeamLogoView(url: driver?.teamLogoUrl, size: 12)
                Text(driver?.teamName ?? "Tap a driver below")
                    .font(.caption2).foregroundStyle(Theme.Palette.textSecondary).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Theme.Space.sm)
        .background(accent.opacity(0.10), in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(accent.opacity(0.5), lineWidth: 1))
    }

    // MARK: Driver picker (chips)
    @ViewBuilder private var driverPicker: some View {
        if vm.loadingDrivers {
            ProgressView().tint(Theme.Palette.racingRed).frame(height: 60)
        } else if vm.drivers.isEmpty {
            Text("Couldn't load drivers for \(String(year)).")
                .font(.subheadline).foregroundStyle(Theme.Palette.textSecondary)
                .frame(maxWidth: .infinity).padding(.vertical, Theme.Space.md)
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 68), spacing: Theme.Space.sm)],
                      spacing: Theme.Space.sm) {
                ForEach(vm.drivers) { d in
                    driverChip(d)
                }
            }
        }
    }

    private func driverChip(_ d: Driver) -> some View {
        let slot = vm.slotFor(d)  // 1, 2, or nil
        let accent: Color = slot == 1 ? Theme.Palette.racingRed
            : slot == 2 ? Theme.Palette.info : Color.team(d.teamColor)
        let selected = slot != nil
        return Button {
            vm.select(d, year: year)
        } label: {
            VStack(spacing: 2) {
                Text(d.code ?? "?")
                    .font(.subheadline.weight(.bold))
                Text(d.teamName ?? "")
                    .font(.caption2).lineLimit(1).minimumScaleFactor(0.9)
            }
            .foregroundStyle(selected ? .black : Theme.Palette.textPrimary)
            .frame(maxWidth: .infinity, minHeight: Theme.minTouch)
            .padding(.horizontal, 4)
            .background(selected ? accent : Theme.Palette.surfaceElevated,
                        in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.sm)
                .stroke(Color.team(d.teamColor), lineWidth: selected ? 0 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(d.fullName)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    // MARK: Results
    @ViewBuilder private var results: some View {
        switch vm.compareState {
        case .idle:
            EmptyStateView(icon: "person.2.badge.gearshape", title: "Pick Two Drivers",
                           message: "Select two drivers above to compare their season head-to-head.")
                .frame(height: 220)
        case .loading:
            LoadingIndicator(label: "Comparing").frame(height: 220)
        case .failed(let msg):
            ErrorState(message: msg) { Task { await vm.compare(year: year) } }.frame(height: 220)
        case .loaded(let resp):
            if resp.drivers.count == 2 {
                comparison(resp.drivers[0], resp.drivers[1])
            }
        }
    }

    private func comparison(_ a: CompareDriver, _ b: CompareDriver) -> some View {
        VStack(spacing: Theme.Space.sm) {
            formLine
            Card {
                VStack(spacing: Theme.Space.sm) {
                    Text("HEAD-TO-HEAD RECORD")
                        .font(.caption.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    h2hBar(label: "Race", aVal: a.raceWinsH2h, bVal: b.raceWinsH2h)
                    h2hBar(label: "Qualifying", aVal: a.qualWinsH2h, bVal: b.qualWinsH2h)
                }
            }
            statRow("Points", a.pointsLabel, b.pointsLabel, a.points, b.points)
            statRow("Wins", "\(a.wins)", "\(b.wins)", Double(a.wins), Double(b.wins))
            statRow("Podiums", "\(a.podiums)", "\(b.podiums)", Double(a.podiums), Double(b.podiums))
            statRow("Poles", "\(a.poles)", "\(b.poles)", Double(a.poles), Double(b.poles))
            statRow("Best Finish", best(a.bestFinish), best(b.bestFinish),
                    a.bestFinish.map { -Double($0) } ?? -99, b.bestFinish.map { -Double($0) } ?? -99)
            statRow("DNFs", "\(a.dnf)", "\(b.dnf)", -Double(a.dnf), -Double(b.dnf))
        }
    }

    // MARK: Form line

    @ViewBuilder private var formLine: some View {
        switch vm.formLineState {
        case .idle, .failed:
            EmptyView()
        case .loading:
            Card { LoadingIndicator(label: "Loading form").frame(height: 120) }
        case .loaded(let series) where series.allSatisfy({ $0.points.isEmpty }):
            EmptyView()
        case .loaded(let series):
            Card {
                VStack(alignment: .leading, spacing: Theme.Space.sm) {
                    Text("FORM LINE").font(.caption.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Chart {
                        ForEach(series) { s in
                            ForEach(s.points) { point in
                                LineMark(x: .value("Round", point.round),
                                         y: .value("Position", -Double(point.position)),
                                         // Both drivers share the round axis, so
                                         // without a series their results join
                                         // into one line that flips between them.
                                         series: .value("Driver", s.code))
                                    .foregroundStyle(Color.team(s.teamColor))
                                    .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                                     lineCap: .round, lineJoin: .round))
                                    .interpolationMethod(.monotone)
                            }
                            ForEach(s.points) { point in
                                PointMark(x: .value("Round", point.round), y: .value("Position", -Double(point.position)))
                                    .foregroundStyle(Color.team(s.teamColor))
                                    .symbolSize(Theme.Chart.pointSize)
                            }
                        }
                    }
                    .chartLegend(.hidden)
                    .chartYAxis {
                        AxisMarks { value in
                            AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                            AxisValueLabel {
                                if let v = value.as(Double.self) { Text("P\(Int(-v))").font(.caption2) }
                            }
                        }
                    }
                    .chartXAxis {
                        AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2) }
                    }
                    .frame(height: Theme.Chart.height(180, typeSize))
                    HStack(spacing: Theme.Space.md) {
                        ForEach(series) { s in
                            HStack(spacing: 4) {
                                Circle().fill(Color.team(s.teamColor)).frame(width: 8, height: 8)
                                Text(s.code).font(.caption2.weight(.bold)).foregroundStyle(Theme.Palette.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    private func h2hBar(label: String, aVal: Int, bVal: Int) -> some View {
        let total = max(aVal + bVal, 1)
        return VStack(spacing: 2) {
            HStack {
                Text("\(aVal)").font(.headline.weight(.bold)).foregroundStyle(Theme.Palette.racingRed)
                Spacer()
                Text(label).font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Text("\(bVal)").font(.headline.weight(.bold)).foregroundStyle(Theme.Palette.info)
            }
            GeometryReader { geo in
                HStack(spacing: 2) {
                    Capsule().fill(Theme.Palette.racingRed)
                        .frame(width: geo.size.width * CGFloat(aVal) / CGFloat(total))
                    Capsule().fill(Theme.Palette.info)
                        .frame(width: geo.size.width * CGFloat(bVal) / CGFloat(total))
                }
            }
            .frame(height: 6)
        }
    }

    private func statRow(_ label: String, _ aText: String, _ bText: String,
                         _ aScore: Double, _ bScore: Double) -> some View {
        HStack {
            Text(aText)
                .font(.system(.headline, design: .rounded).weight(.bold))
                .foregroundStyle(aScore >= bScore ? Theme.Palette.textPrimary : Theme.Palette.textTertiary)
                .frame(maxWidth: .infinity, alignment: .leading)
            if aScore > bScore { winnerDot(Theme.Palette.racingRed) }
            Text(label.uppercased())
                .font(.caption2.weight(.semibold)).foregroundStyle(Theme.Palette.textSecondary)
                .frame(width: 90)
            if bScore > aScore { winnerDot(Theme.Palette.info) }
            Text(bText)
                .font(.system(.headline, design: .rounded).weight(.bold))
                .foregroundStyle(bScore >= aScore ? Theme.Palette.textPrimary : Theme.Palette.textTertiary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private func winnerDot(_ color: Color) -> some View {
        Circle().fill(color).frame(width: 6, height: 6)
    }

    private func best(_ pos: Int?) -> String { pos.map { "P\($0)" } ?? "–" }
}

struct FormLinePoint: Identifiable {
    let round: Int
    let position: Int
    var id: Int { round }
}

struct FormLineSeries: Identifiable {
    let driverId: String
    let code: String
    let teamColor: String?
    let points: [FormLinePoint]
    var id: String { driverId }
}

@MainActor
final class HeadToHeadViewModel: ObservableObject {
    @Published var drivers: [Driver] = []
    @Published var loadingDrivers = false
    @Published var driver1: Driver?
    @Published var driver2: Driver?
    @Published var compareState: Loadable<CompareResponse> = .idle
    @Published var formLineState: Loadable<[FormLineSeries]> = .idle

    func loadDrivers(year: Int) async {
        guard drivers.isEmpty else { return }
        loadingDrivers = true
        defer { loadingDrivers = false }
        do {
            drivers = try await APIClient.shared.drivers(year: year)
        } catch {
            drivers = []
        }
    }

    /// Which slot a driver occupies: 1, 2, or nil.
    func slotFor(_ d: Driver) -> Int? {
        if driver1?.id == d.id { return 1 }
        if driver2?.id == d.id { return 2 }
        return nil
    }

    /// Tap logic: fill slot 1 then slot 2; tapping a selected driver removes it;
    /// tapping a third driver replaces slot 2.
    func select(_ d: Driver, year: Int) {
        Haptics.selection()
        if driver1?.id == d.id {
            driver1 = nil
        } else if driver2?.id == d.id {
            driver2 = nil
        } else if driver1 == nil {
            driver1 = d
        } else if driver2 == nil {
            driver2 = d
        } else {
            driver2 = d
        }
        Task { await compare(year: year) }
        Task { await loadFormLine(year: year) }
    }

    func compare(year: Int) async {
        guard let d1 = driver1, let d2 = driver2, d1.driverId != d2.driverId else {
            compareState = .idle
            return
        }
        compareState = .loading
        do {
            compareState = .loaded(try await APIClient.shared.compare(year: year, d1: d1.driverId, d2: d2.driverId))
        } catch {
            compareState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    /// Round-by-round finishing position for both selected drivers. There's
    /// no per-round series on `/api/compare`, so this fans out one results
    /// request per completed round, same v1 tradeoff the season form guide
    /// makes, filtered down to the two selected drivers.
    func loadFormLine(year: Int) async {
        guard let d1 = driver1, let d2 = driver2, d1.driverId != d2.driverId else {
            formLineState = .idle
            return
        }
        formLineState = .loading
        do {
            let schedule = try await APIClient.shared.schedule(year: year)
            let completedRounds = schedule.filter(\.completed).sorted { $0.round < $1.round }
            guard !completedRounds.isEmpty else {
                formLineState = .loaded([])
                return
            }

            var pointsById: [String: [FormLinePoint]] = [d1.driverId: [], d2.driverId: []]
            try await withThrowingTaskGroup(of: (Int, SessionResultsResponse).self) { group in
                for event in completedRounds {
                    group.addTask {
                        let response = try await APIClient.shared.results(year: year, round: event.round, session: "R")
                        return (event.round, response)
                    }
                }
                for try await (round, response) in group {
                    for entry in response.results {
                        guard let position = entry.position else { continue }
                        if entry.driverId == d1.driverId || entry.abbreviation == d1.code {
                            pointsById[d1.driverId, default: []].append(FormLinePoint(round: round, position: Int(position)))
                        } else if entry.driverId == d2.driverId || entry.abbreviation == d2.code {
                            pointsById[d2.driverId, default: []].append(FormLinePoint(round: round, position: Int(position)))
                        }
                    }
                }
            }

            let s1 = FormLineSeries(driverId: d1.driverId, code: d1.code ?? "?", teamColor: d1.teamColor,
                                     points: (pointsById[d1.driverId] ?? []).sorted { $0.round < $1.round })
            let s2 = FormLineSeries(driverId: d2.driverId, code: d2.code ?? "?", teamColor: d2.teamColor,
                                     points: (pointsById[d2.driverId] ?? []).sorted { $0.round < $1.round })
            formLineState = .loaded([s1, s2])
        } catch {
            formLineState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
