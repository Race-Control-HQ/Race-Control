import XCTest
@testable import RaceControl

/// Swift counterpart of Android's `DownsampleTest`.
final class DownsampleTests: XCTestCase {

    func testShortSeriesIsReturnedUntouched() {
        let xs = [0.0, 1.0, 2.0]
        let ys = [5.0, 6.0, 7.0]
        let output = Downsample.lttb(xs: xs, ys: ys, threshold: 100)
        XCTAssertEqual(output.xs, xs)
        XCTAssertEqual(output.ys, ys)
    }

    func testDownsamplesToTheRequestedSize() {
        let xs = (0..<5_000).map(Double.init)
        let ys = xs.map { sin($0 / 50.0) * 100 }
        let output = Downsample.lttb(xs: xs, ys: ys, threshold: 800)
        XCTAssertEqual(output.xs.count, 800)
        XCTAssertEqual(output.ys.count, 800)
    }

    func testEndpointsAreAlwaysPreserved() {
        let xs = (0..<1_000).map(Double.init)
        let ys = xs.map { $0 * 2 }
        let output = Downsample.lttb(xs: xs, ys: ys, threshold: 50)
        XCTAssertEqual(output.xs.first, xs.first)
        XCTAssertEqual(output.xs.last, xs.last)
        XCTAssertEqual(output.ys.first, ys.first)
        XCTAssertEqual(output.ys.last, ys.last)
    }

    func testKeepsThePeakOfASpike() {
        let xs = (0..<1_000).map(Double.init)
        let ys = xs.map { $0 == 500 ? 300.0 : 100.0 }
        let output = Downsample.lttb(xs: xs, ys: ys, threshold: 100)
        XCTAssertTrue(output.ys.contains(300.0))
    }

    func testXValuesStayMonotonicallyIncreasing() {
        let xs = (0..<2_000).map(Double.init)
        let ys = xs.map { sin($0 / 30.0) }
        let output = Downsample.lttb(xs: xs, ys: ys, threshold: 200)
        for (left, right) in zip(output.xs, output.xs.dropFirst()) {
            XCTAssertGreaterThan(right, left)
        }
    }
}
