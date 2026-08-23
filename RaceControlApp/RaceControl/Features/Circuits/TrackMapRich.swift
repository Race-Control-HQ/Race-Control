import SwiftUI
import Charts

/// Rich circuit map: a speed-coloured racing line with DRS zones highlighted,
/// numbered corners and a start/finish marker. Drawn with Canvas so hundreds of
/// coloured segments stay smooth.
struct RichTrackMap: View {
    let map: CircuitMap

    private var points: [TrackTelemetryPoint] { map.points ?? [] }

    var body: some View {
        Canvas { context, size in
            guard points.count > 1 else { return }
            let rotated = points.map { rotate($0.x, $0.y) }
            let t = transform(for: rotated, size: size)

            let speeds = points.map(\.speed)
            let minS = speeds.min() ?? 0
            let maxS = speeds.max() ?? 1

            // 1) Dark base for contrast
            var base = Path()
            base.addLines(rotated.map(t))
            base.closeSubpath()
            context.stroke(base, with: .color(.black.opacity(0.6)),
                           style: StrokeStyle(lineWidth: 9, lineCap: .round, lineJoin: .round))

            // 2) Speed-coloured segments
            for i in 1..<rotated.count {
                var seg = Path()
                seg.move(to: t(rotated[i - 1]))
                seg.addLine(to: t(rotated[i]))
                let c = Self.speedColor(points[i].speed, min: minS, max: maxS)
                context.stroke(seg, with: .color(c),
                               style: StrokeStyle(lineWidth: 5, lineCap: .round))
            }

            // 3) DRS zones: bright overlay where the flap is open
            for i in 1..<rotated.count where points[i].drsOpen {
                var seg = Path()
                seg.move(to: t(rotated[i - 1]))
                seg.addLine(to: t(rotated[i]))
                context.stroke(seg, with: .color(Theme.Palette.positive),
                               style: StrokeStyle(lineWidth: 8, lineCap: .round))
            }
            // Redraw the speed line thinly over DRS so both read clearly
            for i in 1..<rotated.count where points[i].drsOpen {
                var seg = Path()
                seg.move(to: t(rotated[i - 1]))
                seg.addLine(to: t(rotated[i]))
                context.stroke(seg, with: .color(.white.opacity(0.9)),
                               style: StrokeStyle(lineWidth: 2, lineCap: .round))
            }

            // 4) Corner markers (11pt minimum legible text, markers sized to fit)
            for corner in map.corners {
                let p = t(rotate(corner.x, corner.y))
                let r: CGFloat = 11
                let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
                context.fill(Circle().path(in: rect), with: .color(Theme.Palette.surfaceElevated))
                context.stroke(Circle().path(in: rect), with: .color(Theme.Palette.racingRed), lineWidth: 1.5)
                let label = context.resolve(
                    Text(corner.label).font(.system(size: 11, weight: .bold)).foregroundColor(.white)
                )
                context.draw(label, at: p)
            }

            // 5) Start / finish marker
            if let first = rotated.first {
                let p = t(first)
                let r: CGFloat = 6
                let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
                context.fill(Circle().path(in: rect), with: .color(.white))
                context.stroke(Circle().path(in: rect), with: .color(.black), lineWidth: 2)
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Circuit map")
        .accessibilityValue(mapDescription)
    }

    /// Spoken summary of the map for VoiceOver users.
    private var mapDescription: String {
        var parts = ["\(map.corners.count) corners"]
        if let m = map.lengthMeters, m > 0 {
            parts.append(String(format: "%.3f kilometres", m / 1000))
        }
        let drsZones = zoneCount
        if drsZones > 0 { parts.append("\(drsZones) DRS zones") }
        return parts.joined(separator: ", ")
    }

    private var zoneCount: Int {
        var zones = 0, inZone = false
        for p in points {
            if p.drsOpen, !inZone { zones += 1; inZone = true }
            else if !p.drsOpen { inZone = false }
        }
        return zones
    }

    // MARK: Geometry
    private func rotate(_ x: Double, _ y: Double) -> (Double, Double) {
        let rad = map.rotation * .pi / 180
        return (x * cos(rad) - y * sin(rad), x * sin(rad) + y * cos(rad))
    }

    private func transform(for pts: [(Double, Double)], size: CGSize) -> ((Double, Double)) -> CGPoint {
        let xs = pts.map(\.0), ys = pts.map(\.1)
        let minX = xs.min() ?? 0, maxX = xs.max() ?? 1
        let minY = ys.min() ?? 0, maxY = ys.max() ?? 1
        let spanX = max(maxX - minX, 1), spanY = max(maxY - minY, 1)
        let pad: CGFloat = 26
        let scale = min((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
        let offX = (size.width - spanX * scale) / 2
        let offY = (size.height - spanY * scale) / 2
        return { pt in
            CGPoint(x: (pt.0 - minX) * scale + offX,
                    y: size.height - ((pt.1 - minY) * scale + offY))
        }
    }

    // MARK: Speed colour map (slow → indigo, fast → yellow)
    static func speedColor(_ speed: Double, min: Double, max: Double) -> Color {
        let t = max > min ? (speed - min) / (max - min) : 0.5
        let stops: [(Double, (Double, Double, Double))] = [
            (0.00, (0.29, 0.00, 0.51)),  // indigo
            (0.45, (0.00, 0.55, 0.90)),  // blue
            (0.70, (0.20, 0.85, 0.45)),  // green
            (1.00, (1.00, 0.90, 0.25)),  // yellow
        ]
        var lo = stops[0], hi = stops[stops.count - 1]
        for i in 1..<stops.count where stops[i].0 >= t {
            hi = stops[i]; lo = stops[i - 1]; break
        }
        let span = hi.0 - lo.0
        let f = span > 0 ? (t - lo.0) / span : 0
        let r = lo.1.0 + (hi.1.0 - lo.1.0) * f
        let g = lo.1.1 + (hi.1.1 - lo.1.1) * f
        let b = lo.1.2 + (hi.1.2 - lo.1.2) * f
        return Color(.sRGB, red: r, green: g, blue: b)
    }
}

/// Small horizontal gradient key for the speed colours.
struct SpeedLegend: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            LinearGradient(
                colors: [RichTrackMap.speedColor(0, min: 0, max: 1),
                         RichTrackMap.speedColor(0.5, min: 0, max: 1),
                         RichTrackMap.speedColor(0.7, min: 0, max: 1),
                         RichTrackMap.speedColor(1, min: 0, max: 1)],
                startPoint: .leading, endPoint: .trailing
            )
            .frame(height: 8)
            .clipShape(Capsule())
            HStack {
                Text("Slower").font(.caption2).foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                HStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 2).fill(Theme.Palette.positive).frame(width: 12, height: 4)
                    Text("DRS zone").font(.caption2).foregroundStyle(Theme.Palette.textSecondary)
                }
                Spacer()
                Text("Faster").font(.caption2).foregroundStyle(Theme.Palette.textSecondary)
            }
        }
    }
}

/// Elevation profile of the lap (Z vs distance).
struct TrackElevationProfile: View {
    let points: [TrackTelemetryPoint]

    var body: some View {
        Chart {
            ForEach(points, id: \.distance) { p in
                AreaMark(x: .value("Distance", p.distance),
                         y: .value("Elevation", p.z))
                .foregroundStyle(
                    LinearGradient(colors: [Theme.Palette.info.opacity(0.6), Theme.Palette.info.opacity(0.05)],
                                   startPoint: .top, endPoint: .bottom)
                )
                LineMark(x: .value("Distance", p.distance),
                         y: .value("Elevation", p.z))
                .foregroundStyle(Theme.Palette.info)
                .lineStyle(.init(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
        }
        .chartXAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.Palette.stroke) } }
        .chartYAxis { AxisMarks { _ in
            AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
        } }
        .frame(height: 90)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Elevation profile")
        .accessibilityValue(elevationSummary)
    }

    private var elevationSummary: String {
        let zs = points.map(\.z)
        guard let lo = zs.min(), let hi = zs.max(), hi > lo else { return "Flat circuit" }
        return String(format: "%.0f metres of elevation change across the lap", hi - lo)
    }
}
