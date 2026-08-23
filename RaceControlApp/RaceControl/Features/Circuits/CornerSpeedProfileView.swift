import SwiftUI
import Charts

/// Corner-by-corner speed as a compact bar profile, grouped slow/medium/fast,
/// paired with the circuit outline so a corner number can be matched back to
/// where it actually sits on track. Turns corner markers into a circuit
/// "rhythm" fingerprint.
struct CornerSpeedProfileView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = CornerSpeedProfileViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { map in
            let corners = map.corners.filter { $0.speed != nil }
            if corners.isEmpty {
                EmptyStateView(icon: "chart.bar.fill", title: "No Corner Speed Data",
                               message: "Corner speed cross-references aren't available for this circuit.")
            } else {
                content(map, corners)
            }
        }
        .navigationTitle("Corner Speed Profile")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task(id: "\(year)-\(round)") { await vm.load(year: year, round: round) }
    }

    private func content(_ map: CircuitMap, _ corners: [TrackCorner]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.md) {
                Text("Speed at each corner · km/h · tap a bar for detail")
                    .font(.caption).foregroundStyle(Theme.Palette.textSecondary)

                Chart {
                    ForEach(corners) { corner in
                        BarMark(x: .value("Corner", corner.label), y: .value("Speed", corner.speed ?? 0))
                            .foregroundStyle(Self.tier(corner.speed ?? 0).color)
                    }
                    if let selected = vm.selected {
                        RuleMark(x: .value("Corner", selected.label))
                            .foregroundStyle(Theme.Palette.textPrimary.opacity(0.3))
                    }
                }
                .chartXAxis {
                    // A label per corner overlaps into a grey smear on a
                    // phone; thin them to roughly eight across the axis.
                    AxisMarks { value in
                        if value.index % Theme.Chart.labelStride(corners.count) == 0 {
                            AxisValueLabel().font(.caption2)
                        }
                    }
                }
                .chartYAxis {
                    AxisMarks { _ in
                        AxisGridLine().foregroundStyle(Theme.Palette.stroke); AxisValueLabel().font(.caption2)
                    }
                }
                .frame(height: Theme.Chart.height(240, typeSize))
                .chartOverlay { proxy in
                    GeometryReader { geo in
                        Rectangle().fill(.clear).contentShape(Rectangle())
                            .gesture(DragGesture(minimumDistance: 0).onEnded { value in
                                guard let anchor = proxy.plotFrame else { return }
                                let originX = geo[anchor].origin.x
                                guard let label: String = proxy.value(atX: value.location.x - originX) else { return }
                                if let match = corners.first(where: { $0.label == label }) { vm.select(match) }
                            })
                    }
                }

                legend

                if let selected = vm.selected {
                    detailCard(selected)
                }

                miniMap(map, corners: corners)
            }
            .padding(Theme.Space.md)
        }
    }

    private var legend: some View {
        HStack(spacing: Theme.Space.md) {
            legendSwatch("Slow (<120)", Tier.slow.color)
            legendSwatch("Medium", Tier.medium.color)
            legendSwatch("Fast (>200)", Tier.fast.color)
        }
        .font(.caption2)
        .foregroundStyle(Theme.Palette.textSecondary)
    }

    private func legendSwatch(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 12, height: 8)
            Text(label)
        }
    }

    private func detailCard(_ corner: TrackCorner) -> some View {
        HStack(spacing: Theme.Space.sm) {
            Text("Turn \(corner.label)").font(.subheadline.weight(.bold)).foregroundStyle(Theme.Palette.textPrimary)
            if let speed = corner.speed {
                Text("\(Int(speed)) km/h").font(.subheadline).foregroundStyle(Theme.Palette.textSecondary)
            }
            if let angle = corner.angle {
                Text("\(Int(abs(angle)))° turn").font(.caption).foregroundStyle(Theme.Palette.textTertiary)
            }
            Spacer()
        }
        .padding(Theme.Space.sm)
        .background(Theme.Palette.surfaceElevated, in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
    }

    private func miniMap(_ map: CircuitMap, corners: [TrackCorner]) -> some View {
        Canvas { context, size in
            guard map.outline.count > 1 else { return }
            let rotated = map.outline.map { rotate($0.x, $0.y, degrees: map.rotation) }
            let t = transform(for: rotated, size: size)
            var path = Path()
            path.addLines(rotated.map(t))
            path.closeSubpath()
            context.stroke(path, with: .color(Theme.Palette.stroke), style: StrokeStyle(lineWidth: 8, lineCap: .round, lineJoin: .round))

            for corner in corners {
                let p = t(rotate(corner.x, corner.y, degrees: map.rotation))
                let isSelected = vm.selected?.id == corner.id
                let r: CGFloat = isSelected ? 9 : 6
                let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
                context.fill(Circle().path(in: rect), with: .color(Self.tier(corner.speed ?? 0).color))
                if isSelected {
                    context.stroke(Circle().path(in: rect), with: .color(.white), lineWidth: 2)
                }
            }
        }
        .frame(height: 200)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .accessibilityLabel("Circuit outline with corners colored by speed")
    }

    private func rotate(_ x: Double, _ y: Double, degrees: Double) -> (Double, Double) {
        let rad = degrees * .pi / 180
        return (x * cos(rad) - y * sin(rad), x * sin(rad) + y * cos(rad))
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
            CGPoint(x: (pt.0 - minX) * scale + offX, y: size.height - ((pt.1 - minY) * scale + offY))
        }
    }

    enum Tier {
        case slow, medium, fast
        var color: Color {
            switch self {
            case .slow: Theme.Palette.racingRed
            case .medium: Theme.Palette.warning
            case .fast: Theme.Palette.positive
            }
        }
    }

    static func tier(_ speed: Double) -> Tier {
        if speed < 120 { return .slow }
        if speed < 200 { return .medium }
        return .fast
    }
}

@MainActor
final class CornerSpeedProfileViewModel: ObservableObject {
    @Published var state: Loadable<CircuitMap> = .idle
    @Published var selected: TrackCorner?
    private var loadedKey: String?

    func load(year: Int, round: Int) async {
        let key = "\(year)-\(round)"
        if loadedKey == key, case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.circuitMap(year: year, round: round))
            loadedKey = key
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }

    func select(_ corner: TrackCorner) {
        Haptics.selection()
        selected = corner
    }
}
