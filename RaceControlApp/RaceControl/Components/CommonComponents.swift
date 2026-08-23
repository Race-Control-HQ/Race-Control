import SwiftUI
import Charts

// MARK: - Season picker (menu in the nav bar)

struct SeasonPicker: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        Menu {
            ForEach(appState.seasons, id: \.self) { year in
                Button {
                    appState.selectedYear = year
                } label: {
                    if year == appState.selectedYear {
                        Label(String(year), systemImage: "checkmark")
                    } else {
                        Text(String(year))
                    }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(String(appState.selectedYear))
                    .font(.subheadline.weight(.semibold))
                Image(systemName: "chevron.down")
                    .font(.caption2.weight(.bold))
            }
            .foregroundStyle(Theme.Palette.textPrimary)
            .padding(.horizontal, Theme.Space.sm)
            .frame(minHeight: 32)
            .background(Theme.Palette.surfaceElevated, in: Capsule())
        }
    }
}

// MARK: - Driver avatar (async headshot with graceful fallback)

struct DriverAvatar: View {
    let url: String?
    let initials: String
    let accent: Color
    var size: CGFloat = 48

    var body: some View {
        AsyncImage(url: url.flatMap(URL.init)) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                ZStack {
                    accent.opacity(0.22)
                    Text(initials)
                        .font(.system(size: size * 0.36, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(accent.opacity(0.5), lineWidth: 1.5))
    }
}

// MARK: - Team logo

/// Team logo with a graceful fallback when the hardcoded F1.com CDN URL
/// (see backend `_team_logo_url`) is missing or fails to load: no logo
/// field exists in FastF1/Ergast, so this is a manually maintained mapping
/// that renders nothing rather than a broken-image glyph if it's stale.
/// The source marks are white PNGs, so they sit on a dark chip to stay
/// legible against any background.
struct TeamLogoView: View {
    let url: String?
    var size: CGFloat = 24

    var body: some View {
        if let url, let parsed = URL(string: url) {
            AsyncImage(url: parsed) { phase in
                if case .success(let image) = phase {
                    image.resizable().scaledToFit()
                        .padding(size * 0.12)
                        .frame(width: size, height: size)
                        .background(Color.black.opacity(0.8), in: RoundedRectangle(cornerRadius: 4))
                }
            }
            .frame(width: size, height: size)
        }
    }
}

// MARK: - Team colour accent bar

struct TeamAccentBar: View {
    let color: Color
    var width: CGFloat = 4
    var body: some View {
        RoundedRectangle(cornerRadius: width / 2)
            .fill(color)
            .frame(width: width)
    }
}

// MARK: - Position badge

struct PositionBadge: View {
    let text: String
    var highlight: Bool = false
    var body: some View {
        Text(text)
            .font(.system(.subheadline, design: .rounded).weight(.bold))
            .monospacedDigit()
            .foregroundStyle(highlight ? Color.black : Theme.Palette.textPrimary)
            .frame(width: 34, height: 34)
            .background(
                highlight ? AnyShapeStyle(podiumGradient) : AnyShapeStyle(Theme.Palette.surfaceElevated),
                in: RoundedRectangle(cornerRadius: Theme.Radius.sm)
            )
    }

    private var podiumGradient: LinearGradient {
        LinearGradient(
            colors: [Color(hex: "FFD700"), Color(hex: "FFB300")],
            startPoint: .top, endPoint: .bottom
        )
    }
}

// MARK: - Points pill

struct PointsPill: View {
    let points: String
    var body: some View {
        Text(points.isEmpty ? "0" : points)
            .font(.system(.footnote, design: .rounded).weight(.semibold))
            .monospacedDigit()
            .foregroundStyle(Theme.Palette.textPrimary)
            .padding(.horizontal, Theme.Space.sm)
            .frame(height: 26)
            .background(Theme.Palette.surfaceElevated, in: Capsule())
    }
}

// MARK: - Grid delta indicator (places gained / lost)

struct GridDeltaTag: View {
    let delta: Int
    var body: some View {
        Group {
            if delta == 0 {
                Label("0", systemImage: "equal")
                    .foregroundStyle(Theme.Palette.textTertiary)
            } else if delta > 0 {
                Label("\(delta)", systemImage: "arrow.up")
                    .foregroundStyle(Theme.Palette.positive)
            } else {
                Label("\(abs(delta))", systemImage: "arrow.down")
                    .foregroundStyle(Theme.Palette.negative)
            }
        }
        .font(.caption2.weight(.bold))
        .labelStyle(.titleAndIcon)
    }
}

// MARK: - Tyre badge

struct TyreBadge: View {
    let compound: String?
    var size: CGFloat = 26
    var body: some View {
        Text(TyreCompound.letter(compound))
            .font(.system(size: size * 0.42, weight: .heavy, design: .rounded))
            .foregroundStyle(Color.black)
            .frame(width: size, height: size)
            .background(Circle().fill(TyreCompound.color(compound)))
            .overlay(Circle().stroke(Color.white.opacity(0.25), lineWidth: 1))
    }
}

// MARK: - Section card container

struct Card<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        content
            .padding(Theme.Space.md)
            .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.md)
                    .stroke(Theme.Palette.stroke, lineWidth: 1)
            )
    }
}

// MARK: - Stat cell

struct StatCell: View {
    let value: String
    let label: String
    var accent: Color = Theme.Palette.textPrimary
    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(.title3, design: .rounded).weight(.bold))
                .monospacedDigit()
                .foregroundStyle(accent)
            Text(label.uppercased())
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Theme.Palette.textSecondary)
                .tracking(0.5)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Chart series labels

/// A label pinned to the end of a chart series, in data-space coordinates.
struct ChartSeriesLabel: Identifiable {
    let id: String
    let text: String
    let color: Color
    /// Data-space y of the point the label belongs to.
    let value: Double
    var bold = true
}

/// Vertical de-collision for chart labels.
///
/// Swift Charts' `.annotation` has no collision handling: plot twenty drivers
/// whose final gaps sit within a couple of tenths of each other and the codes
/// stack into an unreadable pile. This spreads them into the nearest set of
/// non-overlapping slots while preserving their original top-to-bottom order.
enum ChartLabelLayout {
    /// - Parameters:
    ///   - desired: preferred y for each label, in view space (top-down).
    ///   - spacing: minimum gap to keep between neighbours.
    ///   - bounds: the vertical range labels must stay inside.
    /// - Returns: adjusted y values, in the same order as `desired`.
    static func spread(_ desired: [CGFloat], spacing: CGFloat,
                       in bounds: ClosedRange<CGFloat>) -> [CGFloat] {
        guard !desired.isEmpty else { return [] }
        let order = desired.indices.sorted { desired[$0] < desired[$1] }
        var placed = order.map { desired[$0] }

        // Sweep down: every label clears the one above it.
        for i in placed.indices.dropFirst() {
            placed[i] = max(placed[i], placed[i - 1] + spacing)
        }
        // If that pushed the stack off the bottom, sweep back up from the end.
        if let last = placed.last, last > bounds.upperBound {
            placed[placed.count - 1] = bounds.upperBound
            for i in placed.indices.dropLast().reversed() {
                placed[i] = min(placed[i], placed[i + 1] - spacing)
            }
        }
        // …which can in turn overflow the top when there's simply no room.
        if let first = placed.first, first < bounds.lowerBound {
            placed[0] = bounds.lowerBound
            for i in placed.indices.dropFirst() {
                placed[i] = max(placed[i], placed[i - 1] + spacing)
            }
        }

        var result = [CGFloat](repeating: 0, count: desired.count)
        for (slot, index) in order.enumerated() { result[index] = placed[slot] }
        return result
    }
}

/// Non-overlapping series labels drawn in a gutter at the right edge of a
/// plot. Use from `.chartOverlay`, and reserve room for it by extending the
/// chart's x domain past the last data point.
struct ChartTrailingLabels: View {
    let proxy: ChartProxy
    let labels: [ChartSeriesLabel]
    /// Data-space x where the label gutter starts.
    let anchorX: Double
    var width: CGFloat = 96

    var body: some View {
        GeometryReader { geo in
            if let anchor = proxy.plotFrame, !labels.isEmpty {
                let plot = geo[anchor]
                let desired = labels.map { CGFloat(proxy.position(forY: $0.value) ?? 0) }
                let placed = ChartLabelLayout.spread(
                    desired, spacing: Theme.Chart.labelSpacing, in: 0...plot.height
                )
                let x = plot.minX + CGFloat(proxy.position(forX: anchorX) ?? 0)

                ForEach(Array(labels.enumerated()), id: \.element.id) { index, label in
                    Text(label.text)
                        .font(label.bold ? .caption2.weight(.bold) : .caption2)
                        .foregroundStyle(label.color)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(width: width, alignment: .leading)
                        .position(x: x + width / 2, y: plot.minY + placed[index])
                }
            }
        }
        .allowsHitTesting(false)
    }
}
