import Foundation

/// A permissive JSON scalar decoder.
///
/// The backend mixes sources: FastF1 emits numbers, Ergast/Jolpica emits the
/// same fields as strings. `JSONValue` decodes whichever arrives and exposes
/// convenient typed accessors so the UI never crashes on a type mismatch.
enum JSONValue: Codable, Hashable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() {
            self = .null
        } else if let v = try? c.decode(Bool.self) {
            self = .bool(v)
        } else if let v = try? c.decode(Int.self) {
            self = .int(v)
        } else if let v = try? c.decode(Double.self) {
            self = .double(v)
        } else if let v = try? c.decode(String.self) {
            self = .string(v)
        } else {
            self = .null
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let v): try c.encode(v)
        case .int(let v): try c.encode(v)
        case .double(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }

    var stringValue: String? {
        switch self {
        case .string(let v): return v
        case .int(let v): return String(v)
        case .double(let v): return String(v)
        case .bool(let v): return String(v)
        case .null: return nil
        }
    }

    var intValue: Int? {
        switch self {
        case .int(let v): return v
        case .double(let v): return Int(v)
        case .string(let v): return Int(v) ?? Double(v).map(Int.init)
        default: return nil
        }
    }

    var doubleValue: Double? {
        switch self {
        case .double(let v): return v
        case .int(let v): return Double(v)
        case .string(let v): return Double(v)
        default: return nil
        }
    }

    /// Formats numbers without a trailing `.0` (points, etc.).
    var numberLabel: String? {
        guard let d = doubleValue else { return stringValue }
        return d.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(d)) : String(d)
    }
}

/// Flexible ISO-8601 date parsing (with and without fractional seconds / zones).
enum ISO8601 {
    private static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    private static let plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
    private static let naive: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()
    private static let dateOnly: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()

    static func flexible(_ string: String?) -> Date? {
        guard let s = string, !s.isEmpty else { return nil }
        return withFraction.date(from: s)
            ?? plain.date(from: s)
            ?? naive.date(from: s)
            ?? dateOnly.date(from: s)
    }

    /// Clock time in the device's local timezone, with the offset appended
    /// (e.g. "1:20:00 PM GMT+1") so it's never ambiguous which zone is being
    /// shown: race-control timestamps come from the timing feed in UTC, but
    /// a race can be happening in any timezone, and the viewer is in another.
    private static let clockWithZoneFormatter: DateFormatter = {
        let f = DateFormatter()
        // `Date.FormatStyle`'s field builders don't expose a timezone-name
        // modifier (there's no `.timeZoneName` on it; that was never a real
        // API), so this uses the older `DateFormatter` template API instead,
        // which does support one via the "zzz" pattern. Template-based rather
        // than a fixed "h:mm:ss a zzz" string so 12h/24h formatting still
        // follows the user's locale. Time zone defaults to the device's
        // current zone, which is what "local timezone" means here.
        f.setLocalizedDateFormatFromTemplate("hmmss zzz")
        return f
    }()

    static func clockWithZone(_ string: String?) -> String? {
        guard let date = flexible(string) else { return nil }
        return clockWithZoneFormatter.string(from: date)
    }
}
