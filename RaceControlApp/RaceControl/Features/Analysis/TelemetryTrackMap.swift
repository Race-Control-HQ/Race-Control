import SwiftUI

/// Mini track map drawn from a telemetry trace's X/Y, with a moving dot per
/// driver at the current replay distance.
struct TelemetryTrackMap: View {
    let traces: [TelemetryTrace]
    let distance: Double

    /// Trace with the most samples gives the smoothest outline.
    private var outlineTrace: TelemetryTrace? {
        traces.max { $0.x.count < $1.x.count }
    }

    var body: some View {
        GeometryReader { geo in
            if let outline = outlineTrace, outline.x.count > 1 {
                let bounds = coordinateBounds(outline)
                let t = transform(bounds: bounds, size: geo.size)

                ZStack {
                    // Track outline
                    Path { path in
                        let pts = zip(outline.x, outline.y).map { pair in t((pair.0, pair.1)) }
                        guard let first = pts.first else { return }
                        path.move(to: first)
                        pts.dropFirst().forEach { path.addLine(to: $0) }
                    }
                    .stroke(Theme.Palette.textTertiary.opacity(0.5),
                            style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))

                    // Moving car dots
                    ForEach(traces) { trace in
                        if let pos = trace.position(atDistance: distance) {
                            let p = t((pos.x, pos.y))
                            Circle()
                                .fill(Color.team(trace.teamColor))
                                .frame(width: 14, height: 14)
                                .overlay(Circle().stroke(.white, lineWidth: 2))
                                .position(p)
                        }
                    }
                }
            }
        }
    }

    private func coordinateBounds(_ trace: TelemetryTrace) -> (minX: Double, maxX: Double, minY: Double, maxY: Double) {
        (trace.x.min() ?? 0, trace.x.max() ?? 1, trace.y.min() ?? 0, trace.y.max() ?? 1)
    }

    private func transform(bounds: (minX: Double, maxX: Double, minY: Double, maxY: Double),
                           size: CGSize) -> ((Double, Double)) -> CGPoint {
        let spanX = max(bounds.maxX - bounds.minX, 1)
        let spanY = max(bounds.maxY - bounds.minY, 1)
        let pad: CGFloat = 16
        let scale = min((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
        let offsetX = (size.width - spanX * scale) / 2
        let offsetY = (size.height - spanY * scale) / 2
        return { pt in
            CGPoint(
                x: (pt.0 - bounds.minX) * scale + offsetX,
                y: size.height - ((pt.1 - bounds.minY) * scale + offsetY) // flip Y
            )
        }
    }
}
