import SwiftUI

/// Central design system for RaceControl.
///
/// Dark-first palette tuned for OLED (near-black surfaces), an F1-red accent,
/// and semantic colours that adapt to light/dark automatically. Spacing and
/// radii follow an 8pt grid per Apple's HIG.
enum Theme {

    // MARK: Brand palette
    enum Palette {
        /// Signature Formula 1 red. Brand fills, tints and large text only:
        /// at 3.6:1 on surface it fails WCAG AA for small text.
        static let racingRed = Color(hex: "E10600")
        static let racingRedDim = Color(hex: "B00500")
        /// Lighter red tint for *small* text (5.9:1 on surface, passes AA).
        static let racingRedText = Color(hex: "FF5A50")

        /// OLED-friendly backgrounds (near-black, avoids scroll smear of pure #000).
        static let background = Color(hex: "0A0A0C")
        static let surface = Color(hex: "16161A")
        static let surfaceElevated = Color(hex: "202027")
        static let stroke = Color.white.opacity(0.08)

        // Text
        // Contrast on surface (#16161A): 16.2:1 / 7.0:1 / 5.3:1; all pass WCAG AA.
        static let textPrimary = Color(hex: "F2F2F5")
        static let textSecondary = Color(hex: "A0A0AA")
        static let textTertiary = Color(hex: "8A8A94")

        // Semantic
        static let positive = Color(hex: "30D158")
        static let negative = Color(hex: "FF453A")
        static let warning = Color(hex: "FF9F0A")
        static let info = Color(hex: "64D2FF")

        // Tyre compounds (official F1 colours)
        static let tyreSoft = Color(hex: "F0402C")
        static let tyreMedium = Color(hex: "F5D000")
        static let tyreHard = Color(hex: "EBEBEB")
        static let tyreIntermediate = Color(hex: "40B14B")
        static let tyreWet = Color(hex: "1E6FE0")

        // Track flags / safety-car periods (official F1-ish flag colours)
        static let flagGreen = Color(hex: "30D158")
        static let flagYellow = Color(hex: "FFD60A")
        static let flagDoubleYellow = Color(hex: "FF9F0A")
        static let flagRed = Color(hex: "FF453A")
        static let flagBlue = Color(hex: "64D2FF")
        static let flagChequered = Color(hex: "F2F2F5")
        static let flagSafetyCar = Color(hex: "FF9F0A")
        static let flagVirtualSafetyCar = Color(hex: "BF5AF2")
    }

    // MARK: Spacing (8pt grid)
    enum Space {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
    }

    // MARK: Corner radii
    enum Radius {
        static let sm: CGFloat = 8
        static let md: CGFloat = 14
        static let lg: CGFloat = 20
        static let pill: CGFloat = 999
    }

    /// Minimum HIG touch target.
    static let minTouch: CGFloat = 44

    // MARK: Charts
    /// Geometry and stroke weights for plots, tuned for a phone-sized canvas.
    ///
    /// Swift Charts' defaults are laid out for a Mac window: hairline strokes,
    /// an axis label per category, and one fixed height regardless of text
    /// size. On a 390pt-wide phone that reads as noise, so every chart in the
    /// app pulls its weights and heights from here instead.
    enum Chart {
        /// Series stroke weight. The 1pt default vanishes against the OLED
        /// background once more than two lines overlap.
        static let lineWidth: CGFloat = 2.5
        /// Stroke weight for a de-emphasised (unfocused) series.
        static let mutedLineWidth: CGFloat = 1.4
        /// Opacity for a de-emphasised series — visible as context, never
        /// competing with the focused one.
        static let mutedOpacity: Double = 0.22
        /// Symbol area for a plotted data point.
        static let pointSize: CGFloat = 34
        /// Minimum vertical gap between two trailing series labels.
        static let labelSpacing: CGFloat = 15

        /// Baseline plot height, grown for accessibility text sizes so the
        /// axis labels don't crowd out the plot itself.
        static func height(_ base: CGFloat, _ size: DynamicTypeSize) -> CGFloat {
            size.isAccessibilitySize ? base * 1.35 : base
        }

        /// Row height for a categorical chart (one row per driver or stop).
        static func rowHeight(_ size: DynamicTypeSize) -> CGFloat {
            size.isAccessibilitySize ? 48 : 34
        }

        /// Show roughly `target` axis labels out of `count` categories, so a
        /// 20-corner or 20-driver axis doesn't collapse into a grey smear.
        static func labelStride(_ count: Int, target: Int = 8) -> Int {
            max(1, Int((Double(count) / Double(max(target, 1))).rounded(.up)))
        }

        /// Evenly spaced ticks from 1 through `last`, about `target` of them.
        ///
        /// `last` is always included — the final lap, round or grid slot is
        /// the one a reader looks for — but it replaces the preceding tick
        /// rather than crowding it, which is what stacked "P21 P22" into an
        /// unreadable pair at the foot of the position axis.
        static func ticks(through last: Int, target: Int = 5) -> [Int] {
            guard last > 1 else { return [1] }
            let step = max(1, Int((Double(last) / Double(max(target, 1))).rounded()))
            var values = Array(stride(from: 1, through: last, by: step))
            if let previous = values.last, previous != last {
                if last - previous < step { values.removeLast() }
                values.append(last)
            }
            return values
        }
    }
}

// MARK: - Hex color support
extension Color {
    /// Create a Color from a hex string like `#E10600` or `E10600`.
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: CharacterSet(charactersIn: "# ")).uppercased()
        var value: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&value)
        let r, g, b, a: Double
        switch cleaned.count {
        case 8: // RRGGBBAA
            r = Double((value >> 24) & 0xFF) / 255
            g = Double((value >> 16) & 0xFF) / 255
            b = Double((value >> 8) & 0xFF) / 255
            a = Double(value & 0xFF) / 255
        default: // RRGGBB (or fallback)
            r = Double((value >> 16) & 0xFF) / 255
            g = Double((value >> 8) & 0xFF) / 255
            b = Double(value & 0xFF) / 255
            a = 1
        }
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }

    /// Build a team colour from an optional backend hex, falling back to red.
    static func team(_ hex: String?) -> Color {
        guard let hex, !hex.isEmpty else { return Theme.Palette.racingRed }
        return Color(hex: hex)
    }
}

// MARK: - Tyre compound helper
enum TyreCompound {
    static func color(_ compound: String?) -> Color {
        switch (compound ?? "").uppercased() {
        case "SOFT": return Theme.Palette.tyreSoft
        case "MEDIUM": return Theme.Palette.tyreMedium
        case "HARD": return Theme.Palette.tyreHard
        case "INTERMEDIATE": return Theme.Palette.tyreIntermediate
        case "WET": return Theme.Palette.tyreWet
        default: return Theme.Palette.textTertiary
        }
    }

    static func letter(_ compound: String?) -> String {
        guard let c = compound?.uppercased().first else { return "?" }
        return String(c)
    }
}

// MARK: - Flag / safety-car period helper
enum FlagStyle {
    /// Colour for a collapsed period's `type` (`YELLOW`, `DOUBLE_YELLOW`, `RED`, `SC`, `VSC`).
    static func color(_ type: String?) -> Color {
        switch (type ?? "").uppercased() {
        case "YELLOW": return Theme.Palette.flagYellow
        case "DOUBLE_YELLOW": return Theme.Palette.flagDoubleYellow
        case "RED": return Theme.Palette.flagRed
        case "SC": return Theme.Palette.flagSafetyCar
        case "VSC": return Theme.Palette.flagVirtualSafetyCar
        default: return Theme.Palette.textTertiary
        }
    }

    static func icon(_ type: String?) -> String {
        switch (type ?? "").uppercased() {
        case "YELLOW", "DOUBLE_YELLOW": return "flag.fill"
        case "RED": return "flag.fill"
        case "SC": return "car.fill"
        case "VSC": return "car.side.fill"
        default: return "flag.fill"
        }
    }

    static func label(_ type: String?) -> String {
        switch (type ?? "").uppercased() {
        case "YELLOW": return "Yellow Flag"
        case "DOUBLE_YELLOW": return "Double Yellow"
        case "RED": return "Red Flag"
        case "SC": return "Safety Car"
        case "VSC": return "Virtual Safety Car"
        default: return type ?? "Flag"
        }
    }

    /// Colour for a raw event's `flag` string (used in the events timeline).
    static func eventColor(_ flag: String?) -> Color {
        switch (flag ?? "").uppercased() {
        case "GREEN": return Theme.Palette.flagGreen
        case "YELLOW": return Theme.Palette.flagYellow
        case "DOUBLE YELLOW": return Theme.Palette.flagDoubleYellow
        case "RED": return Theme.Palette.flagRed
        case "CLEAR": return Theme.Palette.flagGreen
        case "CHEQUERED": return Theme.Palette.flagChequered
        case "BLUE": return Theme.Palette.flagBlue
        case "BLACK AND WHITE": return Theme.Palette.textPrimary
        default: return Theme.Palette.textTertiary
        }
    }
}

// MARK: - Race-control message category helper
/// Icon/colour mapping for the full, unfiltered race-control log (`RaceControlMessage.category`:
/// `Flag`, `SafetyCar`, `Drs`, `CarEvent`, `Other`). Flag/SafetyCar rows with a `flag` value
/// defer to `FlagStyle.eventColor` so colours stay consistent with the Flags screen.
enum RaceControlStyle {
    static func icon(_ category: String?) -> String {
        switch category ?? "" {
        case "Flag": return "flag.fill"
        case "SafetyCar": return "car.fill"
        case "Drs": return "bolt.fill"
        case "CarEvent": return "exclamationmark.triangle.fill"
        case "Other": return "doc.text.fill"
        default: return "info.circle.fill"
        }
    }

    static func color(_ category: String?, flag: String?) -> Color {
        switch category ?? "" {
        case "Flag": return FlagStyle.eventColor(flag)
        case "SafetyCar": return Theme.Palette.flagSafetyCar
        case "Drs": return Theme.Palette.info
        case "CarEvent": return Theme.Palette.warning
        case "Other": return Theme.Palette.textSecondary
        default: return Theme.Palette.textTertiary
        }
    }

    static func label(_ category: String?) -> String {
        switch category ?? "" {
        case "Flag": return "Flag"
        case "SafetyCar": return "Safety Car"
        case "Drs": return "DRS"
        case "CarEvent": return "Car Event"
        case "Other": return "Other"
        default: return category ?? "Unknown"
        }
    }
}
