import XCTest
@testable import RaceControl

/// Swift counterpart of the Android build's `LapTimeFormatTest`. Formatting must
/// match across the two apps exactly: they show the same race, so a difference
/// would be read as one of them being wrong. The cases here are deliberately
/// the same cases, with the same expected strings.
final class LapTimeFormatTests: XCTestCase {

    // MARK: LapTimeFormat.format

    func testLeadingFormatIncludesMinutes() {
        XCTAssertEqual(LapTimeFormat.format(ms: 92_145, leading: true), "1:32.145")
    }

    func testGapFormatFoldsMinutesIntoSeconds() {
        // A lap down can exceed a minute; F1 timing shows 92.145, not 1:32.145.
        XCTAssertEqual(LapTimeFormat.format(ms: 92_145, leading: false), "92.145")
    }

    func testSubMinuteLeadingTimeOmitsTheMinuteField() {
        XCTAssertEqual(LapTimeFormat.format(ms: 32_145, leading: true), "32.145")
    }

    func testMillisecondsAreZeroPadded() {
        // "1:32.045", never "1:32.45" — a truncated field misreads as 450ms.
        XCTAssertEqual(LapTimeFormat.format(ms: 92_045, leading: true), "1:32.045")
    }

    func testSecondsAreZeroPaddedAfterAMinute() {
        XCTAssertEqual(LapTimeFormat.format(ms: 61_002, leading: true), "1:01.002")
    }

    func testZeroFormatsCleanly() {
        XCTAssertEqual(LapTimeFormat.format(ms: 0, leading: true), "0.000")
    }

    // MARK: raceTimeLabel

    func testWinnerShowsAnAbsoluteTime() {
        let winner = makeEntry(position: 1, timeMs: 5_401_234, status: "Finished")
        XCTAssertEqual(winner.raceTimeLabel(winnerTimeMs: 5_401_234), "90:01.234")
    }

    func testOthersShowAGapToTheWinner() {
        let second = makeEntry(position: 2, timeMs: 5_403_456, status: "Finished")
        XCTAssertEqual(second.raceTimeLabel(winnerTimeMs: 5_401_234), "+2.222")
    }

    func testANonFinisherShowsItsStatus() {
        let dnf = makeEntry(position: 18, timeMs: nil, status: "Accident")
        XCTAssertEqual(dnf.raceTimeLabel(winnerTimeMs: 5_401_234), "Accident")
    }

    func testALappedFinisherKeepsThePlusLapStatus() {
        let lapped = makeEntry(position: 15, timeMs: nil, status: "+1 Lap")
        XCTAssertEqual(lapped.raceTimeLabel(winnerTimeMs: 5_401_234), "+1 Lap")
    }

    func testNoWinnerTimeFallsBackToAnAbsoluteTime() {
        // Sprint/practice payloads can lack a leader time; showing the car's own
        // time is better than showing a gap to nothing.
        let entry = makeEntry(position: 4, timeMs: 92_145, status: "Finished")
        XCTAssertEqual(entry.raceTimeLabel(winnerTimeMs: nil), "1:32.145")
    }

    func testNoTimeAndNoStatusShowsADash() {
        let entry = makeEntry(position: 9, timeMs: nil, status: nil)
        XCTAssertEqual(entry.raceTimeLabel(winnerTimeMs: 5_401_234), "–")
    }

    // MARK: positionLabel / pointsLabel / gridDelta

    func testGridDeltaIsPositiveWhenPlacesAreGained() {
        XCTAssertEqual(makeEntry(position: 3, gridPosition: 10).gridDelta, 7)
    }

    func testGridDeltaIsNegativeWhenPlacesAreLost() {
        XCTAssertEqual(makeEntry(position: 12, gridPosition: 4).gridDelta, -8)
    }

    func testAPitLaneStartHasNoMeaningfulDelta() {
        // Grid 0 means a pit-lane start, not "gained 12 places".
        XCTAssertNil(makeEntry(position: 12, gridPosition: 0).gridDelta)
    }

    func testANonNumericClassifiedPositionPassesThrough() {
        XCTAssertEqual(makeEntry(position: 20, classifiedPosition: "R").positionLabel, "R")
    }

    func testANumericClassifiedPositionUsesThePosition() {
        XCTAssertEqual(makeEntry(position: 4, classifiedPosition: "4").positionLabel, "4")
    }

    func testAMissingPositionShowsADash() {
        XCTAssertEqual(makeEntry(position: nil).positionLabel, "–")
    }

    func testZeroPointsRenderAsAnEmptyLabel() {
        XCTAssertEqual(makeEntry(points: 0).pointsLabel, "")
        XCTAssertEqual(makeEntry(points: 25).pointsLabel, "25")
    }

    func testFractionalPointsKeepTheirFraction() {
        // Half points are rare but real (2021 Belgium).
        XCTAssertEqual(makeEntry(points: 12.5).pointsLabel, "12.5")
    }

    // MARK: - Fixture

    /// `ResultEntry` is a `Codable` struct with a wide memberwise initialiser,
    /// so tests build one by decoding a minimal JSON payload. That also exercises
    /// the decoding path the app actually uses.
    private func makeEntry(
        position: Double? = nil,
        classifiedPosition: String? = nil,
        gridPosition: Double? = nil,
        points: Double? = nil,
        timeMs: Int? = nil,
        status: String? = nil
    ) -> ResultEntry {
        var fields: [String] = []
        if let position { fields.append("\"position\": \(position)") }
        if let classifiedPosition { fields.append("\"classifiedPosition\": \"\(classifiedPosition)\"") }
        if let gridPosition { fields.append("\"gridPosition\": \(gridPosition)") }
        if let points { fields.append("\"points\": \(points)") }
        if let timeMs { fields.append("\"timeMs\": \(timeMs)") }
        if let status { fields.append("\"status\": \"\(status)\"") }
        let json = "{\(fields.joined(separator: ","))}"
        // swiftlint:disable:next force_try
        return try! JSONDecoder().decode(ResultEntry.self, from: Data(json.utf8))
    }
}
