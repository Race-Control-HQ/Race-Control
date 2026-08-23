import SwiftUI
import Charts

/// Fastest-lap telemetry with multi-driver comparison (chip toggles, like Lap
/// Times) and a replay that sweeps the lap: a moving dot on a mini track map,
/// live speed/gear readouts, and a playhead across the speed/throttle/gear charts.
struct TelemetryView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = TelemetryViewModel()

    var body: some View {
        Group {
            switch vm.driversState {
            case .idle, .loading:
                LoadingIndicator(label: "Loading drivers")
            case .failed(let msg):
                ErrorState(message: msg) { Task { await vm.loadDrivers(year: year, round: round) } }
            case .loaded(let drivers):
                content(drivers)
            }
        }
        .navigationTitle("Telemetry")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.loadDrivers(year: year, round: round) }
        .onDisappear { vm.stopReplay() }
    }

    private func content(_ drivers: [RaceDriver]) -> some View {
        ScrollView {
            VStack(spacing: Theme.Space.md) {
                driverChips(drivers)
                lapPicker(drivers)

                if vm.loadingTraces { LoadingIndicator(label: "Loading telemetry").frame(height: 160) }

                if vm.traces.isEmpty && !vm.loadingTraces {
                    EmptyStateView(icon: "waveform.path.ecg", title: "Pick a Driver",
                                   message: "Select up to three drivers to compare their fastest-lap telemetry.")
                        .frame(height: 200)
                } else if !vm.traces.isEmpty {
                    flagBanners
                    trackMapCard
                    replayControls
                    liveReadouts
                    if vm.traces.count == 2 {
                        TelemetryComparison(a: vm.traces[0], b: vm.traces[1])
                    }
                    telemetryChart("Speed", unit: "km/h", channel: \.speed, height: 190)
                    telemetryChart("Throttle", unit: "%", channel: \.throttle, height: 110)
                    gearChart
                }
            }
            .padding(Theme.Space.md)
        }
    }

    // MARK: Driver chips (multi-select)
    private func driverChips(_ drivers: [RaceDriver]) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            HStack {
                Text("DRIVERS").font(.caption.weight(.bold)).foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Text("\(vm.selected.count)/3").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Theme.Space.sm) {
                    ForEach(drivers) { d in
                        let code = d.code ?? "?"
                        let on = vm.selected.contains(code)
                        let disabled = !on && vm.selected.count >= 3
                        Button {
                            Task { await vm.toggle(code, year: year, round: round) }
                        } label: {
                            Text(code)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(on ? .black : (disabled ? Theme.Palette.textTertiary : Theme.Palette.textPrimary))
                                .frame(minWidth: 52, minHeight: Theme.minTouch)
                                .padding(.horizontal, Theme.Space.sm)
                                .background(on ? Color.team(d.teamColor) : Theme.Palette.surfaceElevated,
                                            in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                                .overlay(RoundedRectangle(cornerRadius: Theme.Radius.sm)
                                    .stroke(Color.team(d.teamColor), lineWidth: on ? 0 : 1))
                        }
                        .disabled(disabled)
                        .accessibilityLabel(d.fullName ?? code)
                        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                    }
                }
            }
        }
    }

    // MARK: Lap picker (per selected driver, "Fastest" or any specific lap)
    private func lapPicker(_ drivers: [RaceDriver]) -> some View {
        Group {
            if !vm.selected.isEmpty {
                VStack(alignment: .leading, spacing: Theme.Space.sm) {
                    Text("LAP").font(.caption.weight(.bold)).foregroundStyle(Theme.Palette.textSecondary)
                    VStack(spacing: Theme.Space.xs) {
                        ForEach(vm.selected, id: \.self) { code in
                            lapMenu(for: code, drivers: drivers)
                        }
                    }
                }
            }
        }
    }

    private func lapMenu(for code: String, drivers: [RaceDriver]) -> some View {
        let color = Color.team(drivers.first(where: { $0.code == code })?.teamColor)
        return HStack(spacing: Theme.Space.sm) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(code).font(.subheadline.weight(.bold)).foregroundStyle(Theme.Palette.textPrimary)
                .frame(width: 40, alignment: .leading)
            Menu {
                Button {
                    Task { await vm.selectLap("fastest", for: code, year: year, round: round) }
                } label: {
                    Label("Fastest Lap", systemImage: "bolt.fill")
                }
                if !vm.laps(for: code).isEmpty {
                    Divider()
                    ForEach(vm.laps(for: code), id: \.lap) { lp in
                        lapMenuRow(lp, code: code)
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    if let period = vm.currentFlagPeriod(for: code) {
                        Image(systemName: FlagStyle.icon(period.type))
                            .foregroundStyle(FlagStyle.color(period.type))
                            .font(.caption2)
                    }
                    Text(vm.lapLabel(for: code))
                        .font(.footnote.weight(.semibold))
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption2)
                }
                .foregroundStyle(Theme.Palette.textPrimary)
                .padding(.horizontal, Theme.Space.sm)
                .padding(.vertical, 6)
                .background(Theme.Palette.surfaceElevated, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
            }
            .accessibilityLabel("\(code) lap selection")
            .accessibilityValue(vm.lapLabel(for: code))
            Spacer()
        }
    }

    private func lapMenuRow(_ lp: LapTimePoint, code: String) -> some View {
        Group {
            if let period = vm.flagPeriod(forLap: lp.lap) {
                Button {
                    Task { await vm.selectLap("\(lp.lap)", for: code, year: year, round: round) }
                } label: {
                    Label("Lap \(lp.lap) · \(LapFormat.secondsToLap(lp.seconds)) · \(FlagStyle.label(period.type))",
                          systemImage: FlagStyle.icon(period.type))
                }
                .tint(FlagStyle.color(period.type))
            } else {
                Button {
                    Task { await vm.selectLap("\(lp.lap)", for: code, year: year, round: round) }
                } label: {
                    Text("Lap \(lp.lap) · \(LapFormat.secondsToLap(lp.seconds))")
                }
            }
        }
    }

    // MARK: Flag / safety-car badges for the lap(s) currently being viewed
    private var flagBanners: some View {
        let hits: [(TelemetryTrace, FlagPeriod)] = vm.traces.compactMap { trace in
            guard let lap = trace.lapNumber,
                  let period = vm.flagPeriods.first(where: { $0.contains(lap: lap) }) else { return nil }
            return (trace, period)
        }
        return Group {
            if !hits.isEmpty {
                VStack(alignment: .leading, spacing: Theme.Space.xs) {
                    ForEach(hits, id: \.0.id) { trace, period in
                        HStack(spacing: Theme.Space.sm) {
                            Image(systemName: FlagStyle.icon(period.type))
                                .foregroundStyle(FlagStyle.color(period.type))
                            Text("\(trace.code): \(FlagStyle.label(period.type)) (lap \(trace.lapNumber ?? period.startLap))")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Theme.Palette.textPrimary)
                            Spacer()
                        }
                        .padding(.horizontal, Theme.Space.md)
                        .padding(.vertical, Theme.Space.sm)
                        .background(FlagStyle.color(period.type).opacity(0.15),
                                    in: RoundedRectangle(cornerRadius: Theme.Radius.md))
                    }
                }
            }
        }
    }

    // MARK: Track map
    private var trackMapCard: some View {
        TelemetryTrackMap(traces: vm.traces, distance: vm.distance)
            .frame(height: 200)
            .frame(maxWidth: .infinity)
            .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.lg))
    }

    // MARK: Replay controls
    private var replayControls: some View {
        VStack(spacing: Theme.Space.sm) {
            Slider(
                value: Binding(
                    get: { vm.distance },
                    set: { vm.scrub(to: $0) }
                ),
                in: 0...max(vm.maxDistance, 1)
            )
            .tint(Theme.Palette.racingRed)

            HStack {
                Text("\(Int(vm.distance)) m")
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Button { vm.scrub(to: 0) } label: {
                    Image(systemName: "backward.end.fill").font(.title3)
                        .frame(width: Theme.minTouch, height: Theme.minTouch)
                }
                .accessibilityLabel("Back to lap start")
                Button { vm.togglePlay() } label: {
                    Image(systemName: vm.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 46)).foregroundStyle(Theme.Palette.racingRed)
                }
                .accessibilityLabel(vm.isPlaying ? "Pause lap replay" : "Play lap replay")
                .padding(.horizontal, Theme.Space.md)
                Picker("Speed", selection: $vm.speed) {
                    Text("0.5×").tag(0.5); Text("1×").tag(1.0); Text("2×").tag(2.0)
                }
                .pickerStyle(.segmented).frame(width: 150)
            }
            .foregroundStyle(Theme.Palette.textPrimary)
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    // MARK: Live readouts at the playhead
    private var liveReadouts: some View {
        VStack(spacing: Theme.Space.sm) {
            ForEach(vm.traces) { t in
                HStack(spacing: Theme.Space.md) {
                    Circle().fill(Color.team(t.teamColor)).frame(width: 10, height: 10)
                    Text(t.code).font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.Palette.textPrimary).frame(width: 44, alignment: .leading)
                    readout("\(Int(t.speed(atDistance: vm.distance)))", "km/h")
                    readout("\(t.gear(atDistance: vm.distance))", "gear")
                    readout("\(Int(t.throttle(atDistance: vm.distance)))", "thr%")
                    if t.drsOpen(atDistance: vm.distance) {
                        Text("DRS").font(.caption2.weight(.black)).foregroundStyle(.black)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Theme.Palette.positive, in: Capsule())
                    }
                    Spacer(minLength: 0)
                    if let lap = t.lapTime {
                        Text(lap).font(.system(.caption, design: .monospaced))
                            .foregroundStyle(Theme.Palette.textSecondary)
                    }
                }
            }
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private func readout(_ value: String, _ label: String) -> some View {
        VStack(spacing: 0) {
            Text(value).font(.system(.subheadline, design: .rounded).weight(.bold)).monospacedDigit()
                .foregroundStyle(Theme.Palette.textPrimary)
            Text(label).font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
        }
        .frame(minWidth: 48)
    }

    // MARK: Charts (with a moving playhead overlay)
    private func telemetryChart(_ label: String, unit: String,
                                channel: KeyPath<TelemetryTrace, [Double]>, height: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(label) (\(unit))").font(.caption.weight(.semibold))
                .foregroundStyle(Theme.Palette.textSecondary)
            Chart {
                ForEach(vm.traces) { t in
                    ForEach(t.series(t[keyPath: channel])) { sample in
                        LineMark(x: .value("Distance", sample.distance),
                                 y: .value(label, sample.value))
                        .foregroundStyle(Color.team(t.teamColor))
                        .foregroundStyle(by: .value("Driver", t.code))
                        .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                         lineCap: .round, lineJoin: .round))
                    }
                }
            }
            .chartForegroundStyleScale(domain: vm.traces.map(\.code),
                                       range: vm.traces.map { Color.team($0.teamColor) })
            .chartLegend(.hidden)
            .chartXScale(domain: 0...max(vm.maxDistance, 1))
            .chartXAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.Palette.stroke) } }
            .chartYAxis { AxisMarks { _ in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
            } }
            .chartOverlay { proxy in playhead(proxy) }
            .frame(height: height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(label) trace")
            .accessibilityValue(vm.traces.map {
                "\($0.code) \(Int($0[keyPath: channel].max() ?? 0)) peak \(unit)"
            }.joined(separator: ", "))
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private var gearChart: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Gear").font(.caption.weight(.semibold)).foregroundStyle(Theme.Palette.textSecondary)
            Chart {
                ForEach(vm.traces) { t in
                    ForEach(Array(zip(t.distance, t.gear).enumerated()), id: \.offset) { _, pair in
                        LineMark(x: .value("Distance", pair.0), y: .value("Gear", pair.1))
                        .foregroundStyle(Color.team(t.teamColor))
                        .foregroundStyle(by: .value("Driver", t.code))
                        .lineStyle(.init(lineWidth: Theme.Chart.lineWidth, lineJoin: .round))
                        .interpolationMethod(.stepEnd)
                    }
                }
            }
            .chartForegroundStyleScale(domain: vm.traces.map(\.code),
                                       range: vm.traces.map { Color.team($0.teamColor) })
            .chartLegend(.hidden)
            .chartXScale(domain: 0...max(vm.maxDistance, 1))
            .chartYScale(domain: 0...8)
            .chartXAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.Palette.stroke) } }
            .chartYAxis { AxisMarks(values: [1, 3, 5, 7]) { _ in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
            } }
            .chartOverlay { proxy in playhead(proxy) }
            .frame(height: 110)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Gear trace")
            .accessibilityValue("Gear selection across the lap for \(vm.traces.map(\.code).joined(separator: ", "))")
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    /// A vertical playhead drawn over the plot area (cheap: marks don't redraw).
    private func playhead(_ proxy: ChartProxy) -> some View {
        GeometryReader { geo in
            if let x = proxy.position(forX: vm.distance) {
                Path { p in
                    p.move(to: CGPoint(x: x, y: 0))
                    p.addLine(to: CGPoint(x: x, y: geo.size.height))
                }
                .stroke(Color.white.opacity(0.6), lineWidth: 1.5)
            }
        }
    }
}

@MainActor
final class TelemetryViewModel: ObservableObject {
    @Published var driversState: Loadable<[RaceDriver]> = .idle
    @Published var traces: [TelemetryTrace] = []
    @Published var selected: [String] = []
    @Published var loadingTraces = false
    @Published var flagPeriods: [FlagPeriod] = []
    @Published var lapTimes: LapTimesResponse?
    /// Per-driver lap selection: driver code -> "fastest" or a lap number string.
    /// Missing entries default to "fastest", preserving the original behaviour.
    @Published var selectedLap: [String: String] = [:]

    // Replay state
    @Published var distance: Double = 0
    @Published var isPlaying = false
    @Published var speed: Double = 1.0 { didSet { if isPlaying { restartTimer() } } }

    /// Keyed by "code|lapValue" so switching a driver's selected lap doesn't
    /// discard previously-fetched traces (e.g. flicking back to a lap already seen).
    private var cache: [String: TelemetryTrace] = [:]
    private var timer: Timer?
    private let baseLapDuration = 14.0   // seconds to replay a lap at 1×
    private let tick = 0.04              // ~25 fps

    var maxDistance: Double { traces.map(\.maxDistance).max() ?? 0 }

    func loadDrivers(year: Int, round: Int) async {
        if case .loaded = driversState { return }
        driversState = .loading
        do {
            let drivers = try await APIClient.shared.raceDrivers(year: year, round: round)
            driversState = .loaded(drivers)
            if selected.isEmpty, let first = drivers.first?.code {
                await toggle(first, year: year, round: round)
            }
        } catch {
            driversState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
        // Flag overlay is supplementary: failures here shouldn't block telemetry.
        if let flags = try? await APIClient.shared.flags(year: year, round: round) {
            flagPeriods = flags.periods
        }
        // Lap list (times + compounds) powers the lap picker, also supplementary.
        if let laps = try? await APIClient.shared.lapTimes(year: year, round: round) {
            lapTimes = laps
        }
    }

    func toggle(_ code: String, year: Int, round: Int) async {
        Haptics.selection()
        if selected.contains(code) {
            selected.removeAll { $0 == code }
            rebuildTraces()
            return
        }
        guard selected.count < 3 else { return }
        selected.append(code)
        if !(await fetchTrace(for: code, year: year, round: round)) {
            selected.removeAll { $0 == code }  // fetch failed: undo selection
        }
        rebuildTraces()
    }

    /// All known laps for a driver (lap number, time, compound), sourced from
    /// the same lap-times data `LapTimesChartView` uses. Empty if not yet loaded
    /// or the driver isn't found (picker then falls back to "Fastest" only).
    func laps(for code: String) -> [LapTimePoint] {
        lapTimes?.drivers.first(where: { $0.code == code })?.laps ?? []
    }

    func flagPeriod(forLap lap: Int) -> FlagPeriod? {
        flagPeriods.first(where: { $0.contains(lap: lap) })
    }

    /// The flag period (if any) covering the driver's currently-selected lap.
    func currentFlagPeriod(for code: String) -> FlagPeriod? {
        guard let n = Int(lapValue(for: code)) else { return nil }
        return flagPeriod(forLap: n)
    }

    func lapValue(for code: String) -> String { selectedLap[code] ?? "fastest" }

    /// Human-readable label for the lap picker's closed state, e.g. "Fastest" or
    /// "Lap 23 · 1:32.456".
    func lapLabel(for code: String) -> String {
        let value = lapValue(for: code)
        guard let n = Int(value) else { return "Fastest" }
        if let lp = laps(for: code).first(where: { $0.lap == n }) {
            return "Lap \(n) · \(LapFormat.secondsToLap(lp.seconds))"
        }
        return "Lap \(n)"
    }

    /// Switch a driver's selected lap and refetch telemetry for it.
    func selectLap(_ lap: String, for code: String, year: Int, round: Int) async {
        Haptics.selection()
        let previous = lapValue(for: code)
        guard previous != lap else { return }
        selectedLap[code] = lap
        if !(await fetchTrace(for: code, year: year, round: round)) {
            selectedLap[code] = previous  // fetch failed: revert to the prior lap
        }
        rebuildTraces()
    }

    /// Fetches (or reuses a cached) trace for a driver at their currently-selected
    /// lap. Returns whether a trace is available afterwards.
    @discardableResult
    private func fetchTrace(for code: String, year: Int, round: Int) async -> Bool {
        let lap = lapValue(for: code)
        let key = "\(code)|\(lap)"
        if cache[key] != nil { return true }
        loadingTraces = true
        defer { loadingTraces = false }
        if let resp = try? await APIClient.shared.telemetry(year: year, round: round, driver: code, lap: lap),
           let trace = resp.trace {
            cache[key] = trace
            return true
        }
        return false
    }

    private func rebuildTraces() {
        traces = selected.compactMap { cache["\($0)|\(lapValue(for: $0))"] }
        if distance > maxDistance { distance = 0 }
    }

    // MARK: Replay
    func togglePlay() {
        Haptics.impact(.medium)
        isPlaying ? stopReplay() : play()
    }

    private func play() {
        guard maxDistance > 0 else { return }
        if distance >= maxDistance { distance = 0 }
        isPlaying = true
        restartTimer()
    }

    func stopReplay() {
        isPlaying = false
        timer?.invalidate(); timer = nil
    }

    func scrub(to d: Double) {
        stopReplay()
        distance = min(max(d, 0), maxDistance)
    }

    private func restartTimer() {
        timer?.invalidate()
        let step = maxDistance * (tick / baseLapDuration) * speed
        timer = Timer.scheduledTimer(withTimeInterval: tick, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                let next = self.distance + step
                if next >= self.maxDistance { self.distance = self.maxDistance; self.stopReplay() }
                else { self.distance = next }
            }
        }
    }
}
