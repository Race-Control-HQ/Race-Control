import Foundation

/// Distance-indexed lookups so the replay can read each channel at an arbitrary
/// point along the lap. Distances are sorted ascending, so we binary-search.
extension TelemetryTrace {
    var maxDistance: Double { distance.last ?? 0 }

    func sampleIndex(atDistance d: Double) -> Int {
        guard !distance.isEmpty else { return 0 }
        if d <= distance[0] { return 0 }
        if d >= distance[distance.count - 1] { return distance.count - 1 }
        var lo = 0, hi = distance.count - 1
        while lo < hi {
            let mid = (lo + hi) / 2
            if distance[mid] < d { lo = mid + 1 } else { hi = mid }
        }
        if lo > 0, (d - distance[lo - 1]) < (distance[lo] - d) { return lo - 1 }
        return lo
    }

    func position(atDistance d: Double) -> (x: Double, y: Double)? {
        let i = sampleIndex(atDistance: d)
        guard i < x.count, i < y.count else { return nil }
        return (x[i], y[i])
    }
    func speed(atDistance d: Double) -> Double {
        let i = sampleIndex(atDistance: d); return i < speed.count ? speed[i] : 0
    }
    func gear(atDistance d: Double) -> Int {
        let i = sampleIndex(atDistance: d); return i < gear.count ? gear[i] : 0
    }
    func throttle(atDistance d: Double) -> Double {
        let i = sampleIndex(atDistance: d); return i < throttle.count ? throttle[i] : 0
    }
    /// FastF1 DRS codes: 10/12/14 mean the flap is open.
    func drsOpen(atDistance d: Double) -> Bool {
        let i = sampleIndex(atDistance: d)
        guard i < drs.count else { return false }
        return [10, 12, 14].contains(drs[i])
    }

    /// Elapsed lap time (seconds) at a distance, for driver-vs-driver delta.
    func time(atDistance d: Double) -> Double? {
        guard let time, !time.isEmpty else { return nil }
        let i = sampleIndex(atDistance: d)
        return i < time.count ? time[i] : nil
    }

    /// Average speed within a distance window (for mini-sector dominance).
    func averageSpeed(from d0: Double, to d1: Double) -> Double? {
        var sum = 0.0, count = 0
        for (i, dd) in distance.enumerated() where dd >= d0 && dd < d1 {
            if i < speed.count { sum += speed[i]; count += 1 }
        }
        return count > 0 ? sum / Double(count) : nil
    }
}
