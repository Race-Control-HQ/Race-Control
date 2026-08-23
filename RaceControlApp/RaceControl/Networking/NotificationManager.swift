import Foundation
import UserNotifications

/// Schedules on-device reminders for F1 sessions from the calendar.
///
/// Two reminder types per session: "the day before" (09:00 local the previous
/// day) and "15 minutes before" the start. iOS allows at most 64 pending local
/// notifications, so we schedule the soonest sessions and re-run on each launch
/// to keep a rolling window fresh.
final class NotificationManager {
    static let shared = NotificationManager()
    private let center = UNUserNotificationCenter.current()
    private let maxPending = 60

    // MARK: Settings (persisted)
    var isEnabled: Bool { UserDefaults.standard.bool(forKey: "notifications_enabled") }
    private var notifyDayBefore: Bool { boolDefaultTrue("notify_day_before") }
    private var notify1Hour: Bool { boolDefault("notify_1hour", default: false) }
    private var notify15Min: Bool { boolDefaultTrue("notify_15min") }

    // Which session types to remind about.
    private var notifyPractice: Bool { boolDefault("notify_practice", default: false) }
    private var notifyQualifying: Bool { boolDefaultTrue("notify_qualifying") }
    private var notifySprint: Bool { boolDefaultTrue("notify_sprint") }
    private var notifyRace: Bool { boolDefaultTrue("notify_race") }

    private func boolDefaultTrue(_ key: String) -> Bool { boolDefault(key, default: true) }
    private func boolDefault(_ key: String, default def: Bool) -> Bool {
        UserDefaults.standard.object(forKey: key) == nil ? def : UserDefaults.standard.bool(forKey: key)
    }

    /// Whether this session's type is one the user wants reminders for.
    private func wantsSession(_ identifier: String?) -> Bool {
        switch identifier {
        case "FP1", "FP2", "FP3": return notifyPractice
        case "Q": return notifyQualifying
        case "S", "SQ", "SS": return notifySprint
        case "R": return notifyRace
        default: return false
        }
    }

    // MARK: Authorization
    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    // MARK: Scheduling
    func cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    /// Refresh reminders for the current season if the feature is on and allowed.
    func refreshIfEnabled() async {
        guard isEnabled else { return }
        let status = await authorizationStatus()
        guard status == .authorized || status == .provisional else { return }
        let year = Calendar.current.component(.year, from: Date())
        await refresh(year: year)
    }

    func refresh(year: Int) async {
        guard let events = try? await APIClient.shared.schedule(year: year) else { return }

        // Build every candidate reminder for future sessions.
        var candidates: [Reminder] = []
        let now = Date()
        for event in events {
            for session in event.sessions {
                guard wantsSession(session.identifier) else { continue }
                guard let start = session.parsedDate, start > now else { continue }
                let sid = "\(event.year)-\(event.round)-\(session.identifier ?? "?")"

                if notify15Min {
                    let fire = start.addingTimeInterval(-15 * 60)
                    if fire > now {
                        candidates.append(Reminder(
                            id: "\(sid)-soon",
                            fireDate: fire,
                            title: "\(session.reminderName) starting soon",
                            body: "\(event.displayName) · starts \(Self.timeString(start)) (15 min)"
                        ))
                    }
                }
                if notify1Hour {
                    let fire = start.addingTimeInterval(-60 * 60)
                    if fire > now {
                        candidates.append(Reminder(
                            id: "\(sid)-hour",
                            fireDate: fire,
                            title: "\(session.reminderName) in 1 hour",
                            body: "\(event.displayName) · starts \(Self.timeString(start))"
                        ))
                    }
                }
                if notifyDayBefore, let dayBefore = Self.dayBeforeMorning(of: start), dayBefore > now {
                    candidates.append(Reminder(
                        id: "\(sid)-day",
                        fireDate: dayBefore,
                        title: "\(session.reminderName) tomorrow",
                        body: "\(event.displayName) · \(Self.weekdayTimeString(start))"
                    ))
                }
            }
        }

        // Soonest first, capped under the iOS pending limit.
        let scheduled = candidates.sorted { $0.fireDate < $1.fireDate }.prefix(maxPending)

        cancelAll()
        for reminder in scheduled {
            let content = UNMutableNotificationContent()
            content.title = reminder.title
            content.body = reminder.body
            content.sound = .default
            let comps = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: reminder.fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let request = UNNotificationRequest(identifier: reminder.id, content: content, trigger: trigger)
            try? await center.add(request)
        }
    }

    // MARK: Helpers
    private struct Reminder {
        let id: String
        let fireDate: Date
        let title: String
        let body: String
    }

    /// 09:00 local time on the day before the session.
    private static func dayBeforeMorning(of date: Date) -> Date? {
        let cal = Calendar.current
        let sessionMidnight = cal.startOfDay(for: date)
        guard let prevDay = cal.date(byAdding: .day, value: -1, to: sessionMidnight) else { return nil }
        return cal.date(bySettingHour: 9, minute: 0, second: 0, of: prevDay)
    }

    private static func timeString(_ date: Date) -> String {
        date.formatted(.dateTime.hour().minute())
    }
    private static func weekdayTimeString(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.abbreviated).hour().minute())
    }
}

// MARK: - Session display helpers (shared)
extension EventSession {
    var parsedDate: Date? { ISO8601.flexible(date) }

    /// Friendly name used in reminders and schedules.
    var reminderName: String {
        switch identifier {
        case "FP1": return "Practice 1"
        case "FP2": return "Practice 2"
        case "FP3": return "Practice 3"
        case "Q": return "Qualifying"
        case "S": return "Sprint"
        case "SQ", "SS": return "Sprint Qualifying"
        case "R": return "Race"
        default: return name ?? "Session"
        }
    }
}
