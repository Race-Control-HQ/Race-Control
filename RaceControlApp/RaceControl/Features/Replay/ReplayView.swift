import SwiftUI

/// Full-grid race replay. Every available car moves around the circuit while
/// the timing tower changes order on the same continuous replay clock.
struct ReplayView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = ReplayViewModel()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { replay in
            content(replay)
        }
        .navigationTitle("Replay")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
        .onDisappear { vm.stop() }
    }

    @ViewBuilder
    private func content(_ replay: RaceReplay) -> some View {
        if replay.frames.isEmpty {
            EmptyStateView(icon: "play.slash", title: "No Replay Data",
                           message: "Lap timing isn't available for this race.")
        } else {
            VStack(spacing: 0) {
                lapHeader(replay)
                spatialReplay(replay)
                orderList(replay)
                controls(replay)
            }
        }
    }

    @ViewBuilder
    private func spatialReplay(_ replay: RaceReplay) -> some View {
        Group {
            if let map = vm.circuitMap, !map.outline.isEmpty {
                ReplayGridTrackMap(
                    map: map,
                    lapPositions: vm.lapPositions,
                    lapFraction: vm.lapFraction,
                    order: vm.frame?.order ?? [],
                    drivers: replay.drivers
                )
                .frame(height: 250)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
                .overlay(alignment: .topLeading) {
                    Label("LIVE REPLAY", systemImage: "dot.radiowaves.left.and.right")
                        .font(.caption2.weight(.heavy))
                        .foregroundStyle(Theme.Palette.racingRedText)
                        .padding(Theme.Space.sm)
                }
                .overlay(alignment: .bottom) {
                    if vm.lapPositions == nil {
                        Text(vm.isSpatialLoading
                             ? "Loading car positions…"
                             : "Car positions aren't available for this lap.")
                            .font(.caption2)
                            .foregroundStyle(Theme.Palette.textSecondary)
                            .padding(.horizontal, Theme.Space.sm)
                            .padding(.vertical, 6)
                            .background(.ultraThinMaterial, in: Capsule())
                            .padding(Theme.Space.sm)
                    }
                }
            } else if vm.isSpatialLoading {
                HStack(spacing: Theme.Space.sm) {
                    ProgressView()
                    Text("Loading full-grid positions…")
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 120)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            } else {
                Label("Track positions aren't available for this race.", systemImage: "map")
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 64)
                    .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            }
        }
        .padding(.horizontal, Theme.Space.md)
        .padding(.top, Theme.Space.sm)
    }

    private func lapHeader(_ replay: RaceReplay) -> some View {
        VStack(spacing: 2) {
            Text(replay.eventName ?? title)
                .font(.subheadline)
                .foregroundStyle(Theme.Palette.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("LAP")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.Palette.textTertiary)
                Text("\(vm.currentLap)")
                    // Semantic style so the lap counter scales with Dynamic Type.
                    .font(.system(.largeTitle, design: .rounded, weight: .heavy))
                    .monospacedDigit()
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .contentTransition(reduceMotion ? .identity : .numericText())
                Text("/ \(replay.totalLaps)")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
        }
        .padding(.top, Theme.Space.md)
    }

    private func orderList(_ replay: RaceReplay) -> some View {
        ScrollView {
            VStack(spacing: 6) {
                ForEach(vm.frame?.order ?? []) { entry in
                    ReplayRow(entry: entry, previousPosition: vm.previousPosition(for: entry.driver),
                              year: year, driversById: vm.driversById)
                        .transition(.opacity)
                }
            }
            .padding(Theme.Space.md)
            // Position changes animate as cars swap places, unless the user
            // has asked the system to reduce motion.
            .animation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.8),
                       value: vm.currentLap)
        }
    }

    private func controls(_ replay: RaceReplay) -> some View {
        VStack(spacing: Theme.Space.sm) {
            Slider(
                value: Binding(
                    get: { vm.raceProgress },
                    set: { vm.scrub(toProgress: $0) }
                ),
                in: 0...Double(max(replay.totalLaps, 1)),
                onEditingChanged: { editing in
                    if editing { vm.stop() }
                }
            )
            .tint(Theme.Palette.racingRed)

            HStack(spacing: Theme.Space.xl) {
                controlButton("backward.end.fill", label: "Back to first lap") { vm.scrub(to: 1) }
                controlButton("gobackward.5", label: "Back 5 laps") { vm.scrub(to: vm.currentLap - 5) }
                Button {
                    vm.togglePlay()
                } label: {
                    Image(systemName: vm.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(Theme.Palette.racingRed)
                }
                .accessibilityLabel(vm.isPlaying ? "Pause replay" : "Play replay")
                controlButton("goforward.5", label: "Forward 5 laps") { vm.scrub(to: vm.currentLap + 5) }
                controlButton("forward.end.fill", label: "Skip to final lap") { vm.scrub(to: replay.totalLaps) }
            }
            .accessibilityElement(children: .contain)

            // Playback speed
            Picker("Speed", selection: $vm.speed) {
                Text("0.5×").tag(0.5)
                Text("1×").tag(1.0)
                Text("2×").tag(2.0)
                Text("4×").tag(4.0)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, Theme.Space.lg)
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface)
    }

    private func controlButton(_ system: String, label: String,
                               action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: system)
                .font(.title3)
                .foregroundStyle(Theme.Palette.textPrimary)
                .frame(width: Theme.minTouch, height: Theme.minTouch)
        }
        .accessibilityLabel(label)
    }
}

private struct ReplayRow: View {
    let entry: ReplayEntry
    let previousPosition: Int?
    let year: Int
    let driversById: [String: Driver]

    private var accent: Color { .team(entry.teamColor) }
    private var movement: Int? {
        guard let prev = previousPosition else { return nil }
        return prev - entry.position // positive = moved up
    }
    private var matchedDriver: Driver? {
        guard let id = entry.driverId else { return nil }
        return driversById[id]
    }

    var body: some View {
        HStack(spacing: Theme.Space.sm) {
            Text("\(entry.position)")
                .font(.system(.headline, design: .rounded).weight(.bold))
                .monospacedDigit()
                .foregroundStyle(Theme.Palette.textPrimary)
                .frame(width: 30)

            movementIndicator

            TeamAccentBar(color: accent).frame(height: 28)

            driverLabel
                .frame(width: 48, alignment: .leading)

            TeamLogoView(url: entry.teamLogoUrl, size: 18)

            Text(entry.teamName ?? "")
                .font(.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
                .lineLimit(1)

            Spacer(minLength: 4)

            if entry.compound != nil {
                TyreBadge(compound: entry.compound, size: 22)
            }
            if let timing = entry.gap ?? entry.lapTime {
                Text(timing)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(entry.position == 1
                                     ? Theme.Palette.racingRedText
                                     : Theme.Palette.textSecondary)
                    .frame(width: 72, alignment: .trailing)
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, Theme.Space.sm)
        .background(accent.opacity(0.08), in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
    }

    @ViewBuilder private var driverLabel: some View {
        if let matchedDriver {
            NavigationLink {
                DriverDetailView(year: year, driver: matchedDriver)
            } label: {
                Text(entry.driver)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.Palette.textPrimary)
            }
        } else {
            Text(entry.driver)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.Palette.textPrimary)
        }
    }

    @ViewBuilder private var movementIndicator: some View {
        Group {
            if let m = movement, m > 0 {
                Image(systemName: "arrowtriangle.up.fill").foregroundStyle(Theme.Palette.positive)
            } else if let m = movement, m < 0 {
                Image(systemName: "arrowtriangle.down.fill").foregroundStyle(Theme.Palette.negative)
            } else {
                Image(systemName: "minus").foregroundStyle(Theme.Palette.textTertiary)
            }
        }
        .font(.caption2)
        .frame(width: 12)
    }
}

/// Broadcast-style circuit view with every available car drawn from the
/// position samples for the active lap.
private struct ReplayGridTrackMap: View {
    let map: CircuitMap
    let lapPositions: ReplayLapPositions?
    let lapFraction: Double
    let order: [ReplayEntry]
    let drivers: [ReplayDriver]

    var body: some View {
        Canvas { context, size in
            let outline = map.outline.map { rotate(x: $0.x, y: $0.y) }
            guard outline.count > 1 else { return }
            let project = projector(points: outline, size: size)

            var shadow = Path()
            shadow.addLines(outline.map(project))
            shadow.closeSubpath()
            context.stroke(
                shadow,
                with: .color(.black.opacity(0.75)),
                style: StrokeStyle(lineWidth: 12, lineCap: .round, lineJoin: .round)
            )

            var track = Path()
            track.addLines(outline.map(project))
            track.closeSubpath()
            context.stroke(
                track,
                with: .color(Theme.Palette.textTertiary.opacity(0.38)),
                style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round)
            )

            if let start = outline.first {
                let p = project(start)
                let rect = CGRect(x: p.x - 5, y: p.y - 5, width: 10, height: 10)
                context.fill(Circle().path(in: rect), with: .color(.white))
                context.stroke(Circle().path(in: rect), with: .color(.black), lineWidth: 2)
            }

            let metadata = Dictionary(uniqueKeysWithValues: drivers.map { ($0.code, $0) })
            let orderByDriver = Dictionary(uniqueKeysWithValues: order.map { ($0.driver, $0.position) })
            let timingByDriver = Dictionary(uniqueKeysWithValues: order.map { ($0.driver, $0) })
            let cars = lapPositions?.positions.sorted {
                (orderByDriver[$0.key] ?? Int.max) > (orderByDriver[$1.key] ?? Int.max)
            } ?? []

            for (code, samples) in cars {
                let fraction = spatialFraction(for: timingByDriver[code])
                guard let raw = interpolated(samples, fraction: fraction), raw.count >= 2 else { continue }
                let p = project(rotate(x: raw[0], y: raw[1]))
                let color = Color.team(metadata[code]?.teamColor)
                let marker = CGRect(x: p.x - 7, y: p.y - 7, width: 14, height: 14)
                context.fill(Circle().path(in: marker), with: .color(color))
                context.stroke(Circle().path(in: marker), with: .color(.black), lineWidth: 1.5)

                let label = context.resolve(
                    Text(code)
                        .font(.system(size: 9, weight: .heavy, design: .rounded))
                        .foregroundColor(Theme.Palette.textPrimary)
                )
                context.draw(label, at: CGPoint(x: p.x, y: p.y - 13))
            }
        }
        .padding(12)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Full-grid circuit replay")
        .accessibilityValue(accessibilitySummary)
    }

    private var accessibilitySummary: String {
        let shown = lapPositions?.positions.count ?? 0
        guard let leader = order.first?.driver else { return "\(shown) cars on track" }
        return "\(shown) cars on track. \(leader) leads."
    }

    /// Offsets each car behind the leader using the timing-tower gap. Position
    /// traces are sampled independently per driver, so using the same fraction
    /// for everyone would visually bunch the whole field together.
    private func spatialFraction(for entry: ReplayEntry?) -> Double {
        guard
            let gapMs = entry?.gapMs,
            let leaderLapMs = order.first?.lapTimeMs,
            leaderLapMs > 0
        else { return lapFraction }
        let raw = lapFraction - Double(gapMs) / Double(leaderLapMs)
        return raw - floor(raw)
    }

    private func interpolated(_ samples: [[Double]], fraction: Double) -> [Double]? {
        let valid = samples.filter { $0.count >= 2 && $0[0].isFinite && $0[1].isFinite }
        guard let first = valid.first else { return nil }
        guard valid.count > 1 else { return first }
        let position = min(max(fraction, 0), 1) * Double(valid.count - 1)
        let lower = min(Int(floor(position)), valid.count - 1)
        let upper = min(lower + 1, valid.count - 1)
        let amount = position - Double(lower)
        return [
            valid[lower][0] + (valid[upper][0] - valid[lower][0]) * amount,
            valid[lower][1] + (valid[upper][1] - valid[lower][1]) * amount,
        ]
    }

    private func rotate(x: Double, y: Double) -> CGPoint {
        let radians = map.rotation * .pi / 180
        return CGPoint(
            x: x * cos(radians) - y * sin(radians),
            y: x * sin(radians) + y * cos(radians)
        )
    }

    private func projector(points: [CGPoint], size: CGSize) -> (CGPoint) -> CGPoint {
        let xs = points.map(\.x), ys = points.map(\.y)
        let minX = xs.min() ?? 0, maxX = xs.max() ?? 1
        let minY = ys.min() ?? 0, maxY = ys.max() ?? 1
        let spanX = max(maxX - minX, 1), spanY = max(maxY - minY, 1)
        let padding: CGFloat = 24
        let scale = min((size.width - padding * 2) / spanX, (size.height - padding * 2) / spanY)
        let offsetX = (size.width - spanX * scale) / 2
        let offsetY = (size.height - spanY * scale) / 2
        return { point in
            CGPoint(
                x: (point.x - minX) * scale + offsetX,
                y: size.height - ((point.y - minY) * scale + offsetY)
            )
        }
    }
}

@MainActor
final class ReplayViewModel: ObservableObject {
    @Published var state: Loadable<RaceReplay> = .idle
    @Published var raceProgress: Double = 0
    @Published var isPlaying = false
    @Published var driversById: [String: Driver] = [:]
    @Published var speed: Double = 1.0
    @Published var circuitMap: CircuitMap?
    @Published var replayPositions: ReplayPositions?
    @Published var isSpatialLoading = true

    private var replay: RaceReplay?
    private var framesByLap: [Int: ReplayFrame] = [:]
    private var positionsByLap: [Int: ReplayLapPositions] = [:]
    private var timer: Timer?
    private var lastTick: Date?
    private let secondsPerLap = 14.0

    var currentLap: Int {
        guard let replay else { return 1 }
        return min(max(Int(floor(raceProgress)) + 1, 1), max(replay.totalLaps, 1))
    }
    var lapFraction: Double {
        guard let replay else { return 0 }
        if raceProgress >= Double(replay.totalLaps) { return 1 }
        return raceProgress - floor(raceProgress)
    }
    var frame: ReplayFrame? { framesByLap[currentLap] ?? nearestFrame() }
    var lapPositions: ReplayLapPositions? { positionsByLap[currentLap] }

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        isSpatialLoading = true
        async let circuitResult: CircuitMap? = try? await APIClient.shared.circuitMap(year: year, round: round)
        async let positionsResult: ReplayPositions? = try? await APIClient.shared.replayPositions(year: year, round: round)
        async let driversResult: [Driver]? = try? await APIClient.shared.drivers(year: year)
        do {
            let data = try await APIClient.shared.replay(year: year, round: round)
            replay = data
            framesByLap = Dictionary(uniqueKeysWithValues: data.frames.map { ($0.lap, $0) })
            raceProgress = Double(max((data.frames.first?.lap ?? 1) - 1, 0))
            state = .loaded(data)
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
        circuitMap = await circuitResult
        replayPositions = await positionsResult
        positionsByLap = Dictionary(uniqueKeysWithValues: (replayPositions?.laps ?? []).map { ($0.lap, $0) })
        isSpatialLoading = false
        // Secondary lookup for driver-detail navigation; failure shouldn't block replay.
        if let drivers = await driversResult {
            driversById = Dictionary(uniqueKeysWithValues: drivers.map { ($0.driverId, $0) })
        }
    }

    func previousPosition(for driver: String) -> Int? {
        guard currentLap > 1 else { return nil }
        let prev = framesByLap[currentLap - 1] ?? framesByLap.filter { $0.key < currentLap }.max(by: { $0.key < $1.key })?.value
        return prev?.order.first(where: { $0.driver == driver })?.position
    }

    func scrub(to lap: Int) {
        guard let replay else { return }
        let clamped = min(max(lap, 1), max(replay.totalLaps, 1))
        raceProgress = Double(clamped - 1)
    }

    func scrub(toProgress progress: Double) {
        guard let replay else { return }
        raceProgress = min(max(progress, 0), Double(max(replay.totalLaps, 1)))
    }

    func togglePlay() {
        Haptics.impact(.medium)
        isPlaying ? stop() : play()
    }

    private func play() {
        guard let replay else { return }
        if raceProgress >= Double(replay.totalLaps) { raceProgress = 0 }
        isPlaying = true
        restartTimer()
    }

    func stop() {
        isPlaying = false
        timer?.invalidate()
        timer = nil
        lastTick = nil
    }

    private func restartTimer() {
        timer?.invalidate()
        lastTick = Date()
        timer = Timer(timeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.advanceClock() }
        }
        if let timer { RunLoop.main.add(timer, forMode: .common) }
    }

    private func advanceClock() {
        guard let replay else { return }
        let now = Date()
        let elapsed = min(now.timeIntervalSince(lastTick ?? now), 0.25)
        lastTick = now
        raceProgress += elapsed * speed / secondsPerLap
        if raceProgress >= Double(replay.totalLaps) {
            raceProgress = Double(replay.totalLaps)
            stop()
        }
    }

    private func nearestFrame() -> ReplayFrame? {
        framesByLap.filter { $0.key <= currentLap }.max(by: { $0.key < $1.key })?.value
            ?? framesByLap.min(by: { $0.key < $1.key })?.value
    }
}
