import SwiftUI
import Charts

/// Two-driver comparison: a mini-sector dominance strip (who's faster where) and
/// a delta-time chart (running gap between the two fastest laps).
struct TelemetryComparison: View {
    let a: TelemetryTrace
    let b: TelemetryTrace
    private let sectorCount = 20

    private var maxDistance: Double { max(a.maxDistance, b.maxDistance) }

    var body: some View {
        VStack(spacing: Theme.Space.md) {
            miniSectors
            if hasDelta { deltaChart }
        }
    }

    // MARK: Mini-sectors
    private var sectors: [(winner: Int, aSpeed: Double, bSpeed: Double)] {
        (0..<sectorCount).map { k in
            let d0 = maxDistance * Double(k) / Double(sectorCount)
            let d1 = maxDistance * Double(k + 1) / Double(sectorCount)
            let sa = a.averageSpeed(from: d0, to: d1) ?? 0
            let sb = b.averageSpeed(from: d0, to: d1) ?? 0
            return (sa >= sb ? 0 : 1, sa, sb)
        }
    }

    private var miniSectors: some View {
        let secs = sectors
        let aWins = secs.filter { $0.winner == 0 }.count
        let bWins = secs.count - aWins
        return VStack(alignment: .leading, spacing: Theme.Space.sm) {
            Text("MINI-SECTORS").font(.caption.weight(.bold)).tracking(1)
                .foregroundStyle(Theme.Palette.textSecondary)
            HStack(spacing: 2) {
                ForEach(Array(secs.enumerated()), id: \.offset) { _, s in
                    Rectangle()
                        .fill(Color.team(s.winner == 0 ? a.teamColor : b.teamColor))
                        .frame(maxWidth: .infinity)
                        .frame(height: 26)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.sm))
            HStack {
                Label("\(a.code) \(aWins)", systemImage: "square.fill")
                    .font(.caption.weight(.semibold)).foregroundStyle(Color.team(a.teamColor))
                Spacer()
                Text("faster in sector").font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
                Spacer()
                Label("\(bWins) \(b.code)", systemImage: "square.fill")
                    .font(.caption.weight(.semibold)).foregroundStyle(Color.team(b.teamColor))
                    .environment(\.layoutDirection, .rightToLeft)
            }
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Mini-sector comparison")
        .accessibilityValue("\(a.code) faster in \(aWins) of \(secs.count) sectors, \(b.code) faster in \(bWins)")
    }

    // MARK: Delta chart
    private var hasDelta: Bool { a.time?.isEmpty == false && b.time?.isEmpty == false }

    private var deltaSamples: [DeltaSample] {
        let steps = 200
        return (0...steps).compactMap { i in
            let d = maxDistance * Double(i) / Double(steps)
            guard let ta = a.time(atDistance: d), let tb = b.time(atDistance: d) else { return nil }
            return DeltaSample(distance: d, delta: ta - tb)
        }
    }

    private var deltaChart: some View {
        let samples = deltaSamples
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text("DELTA").font(.caption.weight(.bold)).tracking(1)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Text("(\(a.code) vs \(b.code))")
                    .font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            }
            Text("Above zero: \(a.code) is behind · below: ahead")
                .font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
            Chart {
                RuleMark(y: .value("Even", 0))
                    .foregroundStyle(Theme.Palette.textTertiary.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3]))
                ForEach(samples) { s in
                    AreaMark(x: .value("Distance", s.distance), y: .value("Delta", s.delta))
                        .foregroundStyle(
                            LinearGradient(colors: [Color.team(a.teamColor).opacity(0.35), .clear],
                                           startPoint: .top, endPoint: .bottom))
                    LineMark(x: .value("Distance", s.distance), y: .value("Delta", s.delta))
                        .foregroundStyle(Color.team(a.teamColor))
                        .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                         lineCap: .round, lineJoin: .round))
                }
            }
            .chartXAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.Palette.stroke) } }
            .chartYAxis { AxisMarks { value in
                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                AxisValueLabel {
                    if let d = value.as(Double.self) {
                        Text(String(format: "%+.1fs", d)).font(.caption2)
                    }
                }
            } }
            .frame(height: 140)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Delta time chart")
            .accessibilityValue(deltaSummary(samples))
        }
        .padding(Theme.Space.md)
        .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private func deltaSummary(_ samples: [DeltaSample]) -> String {
        guard let final = samples.last?.delta else { return "No delta data" }
        let ahead = final < 0 ? a.code : b.code
        return String(format: "%@ finishes the lap %.3f seconds ahead", ahead, abs(final))
    }
}

private struct DeltaSample: Identifiable {
    let distance: Double
    let delta: Double
    var id: Double { distance }
}
