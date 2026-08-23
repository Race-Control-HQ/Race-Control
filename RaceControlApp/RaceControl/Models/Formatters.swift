import Foundation

/// Lap and race time formatting.
///
/// Deliberately a free-standing type rather than a method on a view: this
/// formatting is the contract the iOS and Android builds share (the Android
/// counterpart is `core/util/LapTimeFormat`), the two apps show the same race,
/// and any divergence reads as one of them being wrong. Keeping it here means
/// it can be unit-tested on both platforms against the same cases.
enum LapTimeFormat {

    /// Formats a millisecond duration as F1 timing shows it.
    ///
    /// - Parameter leading: `true` for an absolute time, which uses a
    ///   `m:ss.SSS` layout once it passes a minute. `false` for a gap, which
    ///   folds minutes back into seconds (`92.145`, not `1:32.145`) because
    ///   that is how a gap over a minute is displayed on the timing screens.
    static func format(ms: Int, leading: Bool) -> String {
        let minutes = ms / 60_000
        let seconds = (ms % 60_000) / 1000
        let millis = ms % 1000
        if leading, minutes > 0 {
            return String(format: "%d:%02d.%03d", minutes, seconds, millis)
        }
        return String(format: "%d.%03d", seconds + minutes * 60, millis)
    }
}

extension ResultEntry {
    /// The time column for a race classification row: an absolute time for the
    /// winner, a gap for everyone else still running, or the status text
    /// ("Accident", "+1 Lap") for a car that has no time of its own.
    func raceTimeLabel(winnerTimeMs: Int?) -> String {
        if let status, status != "Finished", !status.hasPrefix("+"), timeMs == nil {
            return status // DNF, Accident, etc.
        }
        guard let ms = timeMs else { return status ?? "–" }
        if position == 1 {
            return LapTimeFormat.format(ms: ms, leading: true)
        }
        if let winner = winnerTimeMs {
            return "+" + LapTimeFormat.format(ms: ms - winner, leading: false)
        }
        return LapTimeFormat.format(ms: ms, leading: true)
    }
}
