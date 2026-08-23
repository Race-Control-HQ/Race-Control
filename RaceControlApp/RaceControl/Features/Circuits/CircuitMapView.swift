import SwiftUI

/// Renders a circuit outline from the fastest lap's positional trace, with
/// numbered corner markers, the same data FastF1 uses to draw track maps.
struct CircuitMapView: View {
    let year: Int
    let round: Int
    let title: String
    @StateObject private var vm = CircuitMapViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { map in
            if map.outline.isEmpty {
                EmptyStateView(icon: "map", title: "No Track Map",
                               message: "Positional data isn't available for this circuit.")
            } else {
                ScrollView {
                    TrackMapDetail(map: map)
                        .padding(Theme.Space.md)
                }
            }
        }
        .navigationTitle("Track Map")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }
}

/// Shared rich track-map presentation used by the Track Map screen and the
/// circuit detail page: speed-coloured map, stats, legend and elevation profile.
struct TrackMapDetail: View {
    let map: CircuitMap
    private var hasTelemetry: Bool { (map.points?.count ?? 0) > 1 }
    private var drsZones: Int {
        guard let pts = map.points else { return 0 }
        var zones = 0, inZone = false
        for p in pts {
            if p.drsOpen, !inZone { zones += 1; inZone = true }
            else if !p.drsOpen { inZone = false }
        }
        return zones
    }
    private var elevationRange: Double? {
        guard let mn = map.minElevation, let mx = map.maxElevation, mx > mn else { return nil }
        return mx - mn
    }

    var body: some View {
        VStack(spacing: Theme.Space.md) {
            // Map
            Group {
                if hasTelemetry {
                    RichTrackMap(map: map)
                } else {
                    TrackShape(map: map)
                }
            }
            .padding(Theme.Space.md)
            .frame(maxWidth: .infinity)
            .aspectRatio(1, contentMode: .fit)
            .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.lg))

            if hasTelemetry { SpeedLegend() }

            // Stats
            Card {
                HStack {
                    StatCell(value: lengthLabel, label: "Length")
                    Divider().frame(height: 34).overlay(Theme.Palette.stroke)
                    StatCell(value: map.corners.isEmpty ? "–" : "\(map.corners.count)", label: "Corners")
                    Divider().frame(height: 34).overlay(Theme.Palette.stroke)
                    StatCell(value: drsZones > 0 ? "\(drsZones)" : "–", label: "DRS Zones")
                    if let range = elevationRange {
                        Divider().frame(height: 34).overlay(Theme.Palette.stroke)
                        StatCell(value: "\(Int(range)) m", label: "Elevation")
                    }
                }
            }

            if let fl = map.fastestLap, fl.time != nil {
                Card {
                    HStack(spacing: Theme.Space.md) {
                        Image(systemName: "stopwatch.fill").foregroundStyle(Theme.Palette.info)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("FASTEST LAP (map source)")
                                .font(.caption2.weight(.bold)).foregroundStyle(Theme.Palette.textSecondary)
                            Text(fl.driverName ?? fl.driver ?? "–")
                                .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.Palette.textPrimary)
                        }
                        Spacer()
                        Text(fl.time ?? "")
                            .font(.system(.subheadline, design: .monospaced).weight(.bold))
                            .foregroundStyle(Theme.Palette.textPrimary)
                    }
                }
            }

            // Elevation profile
            if let pts = map.points, elevationRange != nil {
                VStack(alignment: .leading, spacing: Theme.Space.sm) {
                    Text("ELEVATION").font(.caption.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    TrackElevationProfile(points: pts)
                }
                .padding(Theme.Space.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            }
        }
    }

    private var lengthLabel: String {
        guard let m = map.lengthMeters, m > 0 else { return "–" }
        return String(format: "%.3f km", m / 1000)
    }
}

struct TrackShape: View {
    let map: CircuitMap

    var body: some View {
        GeometryReader { geo in
            let normalized = normalize(size: geo.size)
            ZStack {
                // Track ribbon
                trackPath(points: normalized.points)
                    .stroke(Theme.Palette.textPrimary,
                            style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
                trackPath(points: normalized.points)
                    .stroke(Theme.Palette.racingRed,
                            style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))

                // Corner markers
                ForEach(normalized.corners) { corner in
                    Circle()
                        .fill(Theme.Palette.surfaceElevated)
                        .frame(width: 22, height: 22)
                        .overlay(Circle().stroke(Theme.Palette.racingRed, lineWidth: 1.5))
                        .overlay(
                            Text(corner.label)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Theme.Palette.textPrimary)
                                .minimumScaleFactor(0.8)
                        )
                        .position(corner.point)
                }
            }
        }
    }

    private func trackPath(points: [CGPoint]) -> Path {
        var path = Path()
        guard let first = points.first else { return path }
        path.move(to: first)
        for p in points.dropFirst() { path.addLine(to: p) }
        path.closeSubpath()
        return path
    }

    private struct Normalized {
        let points: [CGPoint]
        let corners: [(id: String, label: String, point: CGPoint)]
    }

    /// Rotate (per circuit metadata), scale to fit, and flip Y for screen coords.
    private func normalize(size: CGSize) -> (points: [CGPoint], corners: [CornerDot]) {
        let radians = map.rotation * .pi / 180
        func rotate(_ x: Double, _ y: Double) -> (Double, Double) {
            (x * cos(radians) - y * sin(radians), x * sin(radians) + y * cos(radians))
        }

        var pts = map.outline.map { rotate($0.x, $0.y) }
        var corners = map.corners.map { ($0, rotate($0.x, $0.y)) }
        guard !pts.isEmpty else { return ([], []) }

        let xs = pts.map { $0.0 }, ys = pts.map { $0.1 }
        let minX = xs.min()!, maxX = xs.max()!, minY = ys.min()!, maxY = ys.max()!
        let spanX = max(maxX - minX, 1), spanY = max(maxY - minY, 1)
        let span = max(spanX, spanY)
        let scale = min(size.width, size.height) / span
        let offsetX = (size.width - spanX * scale) / 2
        let offsetY = (size.height - spanY * scale) / 2

        func toScreen(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(
                x: (x - minX) * scale + offsetX,
                y: size.height - ((y - minY) * scale + offsetY) // flip Y
            )
        }
        let screenPoints = pts.map { toScreen($0.0, $0.1) }
        let cornerDots = corners.map { CornerDot(id: $0.0.id, label: $0.0.label, point: toScreen($0.1.0, $0.1.1)) }
        return (screenPoints, cornerDots)
    }

    private struct CornerDot: Identifiable {
        let id: String
        let label: String
        let point: CGPoint
    }
}

@MainActor
final class CircuitMapViewModel: ObservableObject {
    @Published var state: Loadable<CircuitMap> = .idle

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.circuitMap(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
