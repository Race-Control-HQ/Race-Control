import Foundation

/// Largest-Triangle-Three-Buckets downsampling.
///
/// A telemetry trace contains thousands of samples per channel. LTTB preserves
/// visually significant points such as braking peaks while capping the number
/// of marks handed to Swift Charts. This mirrors Android's `Downsample` helper
/// so both clients make the same trade-off.
enum Downsample {

    static func lttb(
        xs: [Double],
        ys: [Double],
        threshold: Int
    ) -> (xs: [Double], ys: [Double]) {
        let count = min(xs.count, ys.count)
        guard threshold < count, threshold >= 3, count >= 3 else {
            return (Array(xs.prefix(count)), Array(ys.prefix(count)))
        }

        var outputX: [Double] = []
        var outputY: [Double] = []
        outputX.reserveCapacity(threshold)
        outputY.reserveCapacity(threshold)

        let bucketWidth = Double(count - 2) / Double(threshold - 2)
        var selectedIndex = 0
        outputX.append(xs[0])
        outputY.append(ys[0])

        for bucket in 0..<(threshold - 2) {
            let averageStart = Int(Double(bucket + 1) * bucketWidth) + 1
            let averageEnd = min(Int(Double(bucket + 2) * bucketWidth) + 1, count)
            let averageCount = max(averageEnd - averageStart, 1)

            var averageX = 0.0
            var averageY = 0.0
            if averageStart < averageEnd {
                for index in averageStart..<averageEnd {
                    averageX += xs[index]
                    averageY += ys[index]
                }
            }
            averageX /= Double(averageCount)
            averageY /= Double(averageCount)

            let rangeStart = Int(Double(bucket) * bucketWidth) + 1
            let rangeEnd = min(Int(Double(bucket + 1) * bucketWidth) + 1, count)
            let pointAX = xs[selectedIndex]
            let pointAY = ys[selectedIndex]

            var maximumArea = -1.0
            var maximumIndex = rangeStart
            if rangeStart < rangeEnd {
                for index in rangeStart..<rangeEnd {
                    let area = abs(
                        (pointAX - averageX) * (ys[index] - pointAY)
                            - (pointAX - xs[index]) * (averageY - pointAY)
                    ) * 0.5
                    if area > maximumArea {
                        maximumArea = area
                        maximumIndex = index
                    }
                }
            }

            outputX.append(xs[maximumIndex])
            outputY.append(ys[maximumIndex])
            selectedIndex = maximumIndex
        }

        outputX.append(xs[count - 1])
        outputY.append(ys[count - 1])
        return (outputX, outputY)
    }

    static func cap(
        xs: [Double],
        ys: [Double],
        maximum: Int = 800
    ) -> (xs: [Double], ys: [Double]) {
        lttb(xs: xs, ys: ys, threshold: maximum)
    }
}
