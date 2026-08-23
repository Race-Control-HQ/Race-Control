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
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) * 0.34
            for ring in 1...4 {
                context.stroke(polygon(center, radius * Double(ring) / 4, count: axes.count),
                               with: .color(Theme.Palette.stroke), lineWidth: 1)
            }
            for index in axes.indices {
                let angle = Double(index) / Double(axes.count) * .pi * 2 - .pi / 2
                let end = CGPoint(x: center.x + cos(angle) * radius,
                                  y: center.y + sin(angle) * radius)
                var spoke = Path(); spoke.move(to: center); spoke.addLine(to: end)
                context.stroke(spoke, with: .color(Theme.Palette.stroke), lineWidth: 1)
                let labelPoint = CGPoint(x: center.x + cos(angle) * radius * 1.28,
                                         y: center.y + sin(angle) * radius * 1.28)
                context.draw(Text(axes[index].label).font(.caption2)
                    .foregroundStyle(Theme.Palette.textSecondary), at: labelPoint)
            }
            var shape = Path()
            for (index, axis) in axes.enumerated() {
                let angle = Double(index) / Double(axes.count) * .pi * 2 - .pi / 2
                let valueRadius = radius * Double(axis.percentile) / 100
                let point = CGPoint(x: center.x + cos(angle) * valueRadius,
                                    y: center.y + sin(angle) * valueRadius)
                if index == 0 { shape.move(to: point) } else { shape.addLine(to: point) }
            }
            shape.closeSubpath()
            context.fill(shape, with: .color(accent.opacity(0.28)))
            context.stroke(shape, with: .color(accent), lineWidth: 2)
        }
    }

    private func polygon(_ center: CGPoint, _ radius: Double, count: Int) -> Path {
        var path = Path()
        for index in 0..<count {
            let angle = Double(index) / Double(count) * .pi * 2 - .pi / 2
            let point = CGPoint(x: center.x + cos(angle) * radius,
                                y: center.y + sin(angle) * radius)
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}
