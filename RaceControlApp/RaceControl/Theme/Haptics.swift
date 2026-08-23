import UIKit

/// Lightweight haptic feedback helpers.
///
/// Used sparingly and intentionally (selection changes, transport controls and
/// favouriting) to avoid haptic fatigue. Generators are created per call and
/// prepared, which is cheap and avoids holding state.
enum Haptics {
    /// Picker / chip / segment selection changed.
    static func selection() {
        let generator = UISelectionFeedbackGenerator()
        generator.prepare()
        generator.selectionChanged()
    }

    /// Standard tap confirmation (play/pause, toggles).
    static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .medium) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.prepare()
        generator.impactOccurred()
    }

    /// A completed / positive action (adding a favourite).
    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    /// Something went wrong.
    static func error() {
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }
}
