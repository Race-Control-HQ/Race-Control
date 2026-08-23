import SwiftUI

/// Paints the racing line by brake / coast / partial-throttle / full-throttle
/// intensity, turning already-decoded telemetry channels into a spatial read
/// on driving style: braking zones, lift-and-coast, and confidence on
/// exit — corner by corner, at a glance, without reading a chart.
struct BrakeThrottleMapView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = BrakeThrottleViewModel()

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
        .navigationTitle("Brake / Throttle")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.loadDrivers(year: year, round: round) }
    }

    private func content(_ drivers: [RaceDriver]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.md) {
                driverChips(drivers)

                switch vm.traceState {
                case .idle:
                    EmptyStateView(icon: "flame.fill", title: "Pick a Driver",
                                   message: "Select a driver to paint their fastest lap by brake and throttle.")
                        .frame(height: 220)
                case .loading:
                    LoadingIndicator(label: "Loading telemetry").frame(height: 220)
                case .failed(let msg):
                    ErrorState(message: msg) {
                        Task {
                            if let code = vm.selectedCode { await vm.loadTrace(code: code, year: year, round: round) }
                        }
                    }
                case .loaded(let trace):
                    map(trace)
                    legend
                }
            }
            .padding(Theme.Space.md)
        }
    }

    private func driverChips(_ drivers: [RaceDriver]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Theme.Space.sm) {
                ForEach(drivers) { d in
                    let code = d.code ?? "?"
                    let on = vm.selectedCode == code
                    Button {
                        Task { await vm.select(code: code, year: year, round: round) }
                    } label: {
                        Text(code)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(on ? .black : Theme.Palette.textPrimary)
                            .frame(minWidth: 52, minHeight: Theme.minTouch)
                            .padding(.horizontal, Theme.Space.sm)
                            .background(on ? Color.team(d.teamColor) : Theme.Palette.surfaceElevated,
                                        in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.sm)
                                .stroke(Color.team(d.teamColor), lineWidth: on ? 0 : 1))
                    }
                    .accessibilityLabel(d.fullName ?? code)
                    .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                }
            }
        }
    }

    private func map(_ trace: TelemetryTrace) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            if let lapTime = trace.lapTime {
                Text("\(trace.code) · fastest lap · \(lapTime)")
                    .font(.caption.weight(.bold)).foregroundStyle(Theme.Palette.textSecondary)
            }
            BrakeThrottleCanvas(trace: trace)
                .frame(height: 260)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        }
    }

    private var legend: some View {
        HStack(spacing: Theme.Space.md) {
            legendSwatch("Brake", ControlZone.brake.color)
            legendSwatch("Coast", ControlZone.coast.color)
            legendSwatch("Partial", ControlZone.partial.color)
            legendSwatch("Full throttle", ControlZone.full.color)
        }
        .font(.caption2)
        .foregroundStyle(Theme.Palette.textSecondary)
    }

    private func legendSwatch(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 4) {
            Capsule().fill(color).frame(width: 14, height: 5)
            Text(label)
        }
    }
}

// MARK: - Control zone classification

private enum ControlZone {
    case brake, coast, partial, full

    static func classify(brake: Int, throttle: Double) -> ControlZone {
        if brake > 0 { return .brake }
        if throttle < 10 { return .coast }
        if throttle < 90 { return .partial }
        return .full
    }

    var color: Color {
        switch self {
        case .brake: Theme.Palette.racingRed
        case .coast: Theme.Palette.info
        case .partial: Theme.Palette.warning
        case .full: Theme.Palette.positive
        }
    }
}

/// Canvas-drawn racing line colored per point by brake/throttle zone, same
/// base+colored-segment technique as the circuit speed map.
private struct BrakeThrottleCanvas: View {
    let trace: TelemetryTrace

    var body: some View {
        Canvas { context, size in
            let points = zip(trace.x, trace.y).map { $0 }
            guard points.count > 1 else { return }
            let t = transform(for: points, size: size)

            var base = Path()
            base.addLines(points.map(t))
            context.stroke(base, with: .color(.black.opacity(0.55)),
                           style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))

            let count = min(points.count, trace.brake.count, trace.throttle.count)
            guard count > 1 else { return }
            for i in 1..<count {
                var seg = Path()
                seg.move(to: t(points[i - 1]))
                seg.addLine(to: t(points[i]))
                let zone = ControlZone.classify(brake: trace.brake[i], throttle: trace.throttle[i])
                context.stroke(seg, with: .color(zone.color),
                               style: StrokeStyle(lineWidth: 5, lineCap: .round))
            }

            if let start = points.first {
                let p = t(start)
                let r: CGFloat = 5
                let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
                context.fill(Circle().path(in: rect), with: .color(.white))
                context.stroke(Circle().path(in: rect), with: .color(.black), lineWidth: 2)
            }
        }
        .accessibilityElement()
        .accessibilityLabel("\(trace.code) fastest-lap track map, colored by brake and throttle intensity")
    }

    private func transform(for pts: [(Double, Double)], size: CGSize) -> ((Double, Double)) -> CGPoint {
        let xs = pts.map(\.0), ys = pts.map(\.1)
        let minX = xs.min() ?? 0, maxX = xs.max() ?? 1
        let minY = ys.min() ?? 0, maxY = ys.max() ?? 1
        let spanX = max(maxX - minX, 1), spanY = max(maxY - minY, 1)
        let pad: CGFloat = 20
        let scale = min((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
        let offX = (size.width - spanX * scale) / 2
        let offY = (size.height - spanY * scale) / 2
        return { pt in
            CGPoint(x: (pt.0 - minX) * scale + offX,
                    y: size.height - ((pt.1 - minY) * scale + offY))
        }
    }
}

// MARK: - View model

@MainActor
final class BrakeThrottleViewModel: ObservableObject {
    @Published var driversState: Loadable<[RaceDriver]> = .idle
    @Published var traceState: Loadable<TelemetryTrace> = .idle
    @Published var selectedCode: String?

    private var loadedKey: String?
    private var traceCache: [String: TelemetryTrace] = [:]

    func loadDrivers(year: Int, round: Int) async {
        let key = "\(year)-\(round)"
        if loadedKey == key, case .loaded = driversState { return }
        driversState = .loading
        do {
            let drivers = try await APIClient.shared.raceDrivers(year: year, round: round)
            driversState = .loaded(drivers)
            loadedKey = key
            if let first = drivers.first?.code {
                await select(code: first, year: year, round: round)
            }
        } catch {
            driversState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func select(code: String, year: Int, round: Int) async {
        Haptics.selection()
        selectedCode = code
        await loadTrace(code: code, year: year, round: round)
    }

    func loadTrace(code: String, year: Int, round: Int) async {
        if let cached = traceCache[code] {
            traceState = .loaded(cached)
            return
        }
        traceState = .loading
        do {
            let response = try await APIClient.shared.telemetry(year: year, round: round, driver: code)
            guard let trace = response.trace else {
                traceState = .failed("No telemetry available for \(code) in this session.")
                return
            }
            traceCache[code] = trace
            traceState = .loaded(trace)
        } catch {
            traceState = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
