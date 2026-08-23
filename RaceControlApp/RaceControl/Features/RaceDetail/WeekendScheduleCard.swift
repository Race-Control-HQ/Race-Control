import SwiftUI

/// The full session schedule for a race weekend (FP1/2/3, Sprint sessions,
/// Qualifying and the Race) with each start time shown in the device's local
/// timezone. Works for past and upcoming events.
struct WeekendScheduleCard: View {
    let event: RaceEvent

    /// Sessions in chronological order (backend already orders them).
    private var sessions: [EventSession] { event.sessions }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            HStack {
                Text("WEEKEND SCHEDULE")
                    .font(.caption.weight(.bold)).tracking(1)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Text("Your time")
                    .font(.caption2)
                    .foregroundStyle(Theme.Palette.textTertiary)
            }

            VStack(spacing: 0) {
                ForEach(Array(sessions.enumerated()), id: \.offset) { index, session in
                    SessionRow(session: session, isRace: session.identifier == "R")
                    if index < sessions.count - 1 {
                        Divider().overlay(Theme.Palette.stroke)
                    }
                }
            }
            .background(Theme.Palette.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md).stroke(Theme.Palette.stroke, lineWidth: 1))

            if let tz = TimeZone.current.abbreviation() {
                Text("Times shown in your local timezone (\(tz)).")
                    .font(.caption2)
                    .foregroundStyle(Theme.Palette.textTertiary)
            }
        }
    }
}

private struct SessionRow: View {
    let session: EventSession
    let isRace: Bool

    private var date: Date? { ISO8601.flexible(session.date) }
    private var isPast: Bool { date.map { $0 < Date() } ?? false }

    var body: some View {
        HStack(spacing: Theme.Space.md) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(isRace ? Theme.Palette.racingRed : Theme.Palette.textSecondary)
                .frame(width: 24)

            Text(displayName)
                .font(isRace ? .headline : .subheadline)
                .fontWeight(isRace ? .bold : .medium)
                .foregroundStyle(Theme.Palette.textPrimary)

            Spacer()

            VStack(alignment: .trailing, spacing: 1) {
                if let date {
                    Text(date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)))
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Text(date.formatted(.dateTime.hour().minute()))
                        .font(.subheadline.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(isPast ? Theme.Palette.textTertiary : Theme.Palette.textPrimary)
                } else {
                    Text("TBC").font(.subheadline).foregroundStyle(Theme.Palette.textTertiary)
                }
            }

            if isPast {
                Image(systemName: "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.positive.opacity(0.7))
            }
        }
        .padding(.vertical, Theme.Space.sm)
        .padding(.horizontal, Theme.Space.md)
        .opacity(isPast ? 0.75 : 1)
    }

    /// Friendlier session label than the raw FastF1 name.
    private var displayName: String {
        switch session.identifier {
        case "FP1": return "Practice 1"
        case "FP2": return "Practice 2"
        case "FP3": return "Practice 3"
        case "Q": return "Qualifying"
        case "S": return "Sprint"
        case "SQ", "SS": return "Sprint Qualifying"
        case "R": return "Race"
        default: return session.name ?? "Session"
        }
    }

    private var icon: String {
        switch session.identifier {
        case "FP1", "FP2", "FP3": return "timer"
        case "Q", "SQ", "SS": return "stopwatch"
        case "S": return "bolt.fill"
        case "R": return "flag.checkered"
        default: return "calendar"
        }
    }
}
