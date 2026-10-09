import SwiftUI

struct DriverFingerprintView: View {
    let year: Int
    let driverId: String
    let accent: Color
    @State private var data: DriverFingerprintResponse?

    var body: some View {
        Group {
            if let data, data.available, data.axes.count == 6 {
                Card {
                    VStack(alignment: .leading, spacing: Theme.Space.sm) {
                        Text("SEASON FINGERPRINT")
                            .font(.caption.weight(.bold)).tracking(1)
                            .foregroundStyle(Theme.Palette.textSecondary)
                        FingerprintRadar(axes: data.axes, accent: accent)
                            .frame(height: 310)
                    }
                }
            }
        }
        .task(id: "\(year)-\(driverId)") {
            data = try? await APIClient.shared.driverFingerprint(year: year, driverId: driverId)
        }
    }
}

private struct FingerprintRadar: View {
    let axes: [FingerprintAxis]
    let accent: Color

    var body: some View {
        Canvas { context, size in
            draw(in: &context, size: size)
        }
    }

    // Kept out of the Canvas closure, with explicit types, so the type checker
    // doesn't have to solve the mixed CGFloat/Double geometry in one expression
    // (Xcode 16 gives up on it otherwise).
    private func draw(in context: inout GraphicsContext, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius: Double = Double(min(size.width, size.height)) * 0.34
        let count: Int = axes.count
        for ring in 1...4 {
            let ringRadius: Double = radius * Double(ring) / 4
            context.stroke(polygon(center, ringRadius, count: count),
                           with: .color(Theme.Palette.stroke), lineWidth: 1)
        }
        for index in axes.indices {
            let end = vertex(center, radius, index: index, count: count)
            var spoke = Path(); spoke.move(to: center); spoke.addLine(to: end)
            context.stroke(spoke, with: .color(Theme.Palette.stroke), lineWidth: 1)
            let labelPoint = vertex(center, radius * 1.28, index: index, count: count)
            context.draw(Text(axes[index].label).font(.caption2)
                .foregroundStyle(Theme.Palette.textSecondary), at: labelPoint)
        }
        var shape = Path()
        for (index, axis) in axes.enumerated() {
            let valueRadius: Double = radius * Double(axis.percentile) / 100
            let point = vertex(center, valueRadius, index: index, count: count)
            if index == 0 { shape.move(to: point) } else { shape.addLine(to: point) }
        }
        shape.closeSubpath()
        context.fill(shape, with: .color(accent.opacity(0.28)))
        context.stroke(shape, with: .color(accent), lineWidth: 2)
    }

    /// Point for axis `index` of `count`, `radius` from `center`, starting at 12 o'clock.
    private func vertex(_ center: CGPoint, _ radius: Double, index: Int, count: Int) -> CGPoint {
        let angle: Double = Double(index) / Double(count) * 2 * Double.pi - Double.pi / 2
        let x: Double = Double(center.x) + cos(angle) * radius
        let y: Double = Double(center.y) + sin(angle) * radius
        return CGPoint(x: x, y: y)
    }

    private func polygon(_ center: CGPoint, _ radius: Double, count: Int) -> Path {
        var path = Path()
        for index in 0..<count {
            let point = vertex(center, radius, index: index, count: count)
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}
