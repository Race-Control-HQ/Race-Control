import XCTest
@testable import RaceControl

/// Swift counterpart of the Android build's `JsonValueTest`.
///
/// The backend mixes sources: FastF1 emits these fields as numbers, Ergast and
/// Jolpica emit the same fields as strings. `JSONValue` has to absorb whichever
/// arrives, across 2018-present, without the UI ever seeing a type mismatch.
final class JSONValueTests: XCTestCase {

    private struct Holder: Codable {
        let position: JSONValue?
    }

    private func decode(_ json: String) throws -> Holder {
        try JSONDecoder().decode(Holder.self, from: Data(json.utf8))
    }

    func testDecodesAnInteger() throws {
        let h = try decode(#"{"position": 3}"#)
        XCTAssertEqual(h.position?.intValue, 3)
        XCTAssertEqual(h.position?.stringValue, "3")
        XCTAssertEqual(h.position?.numberLabel, "3")
    }

    func testDecodesANumericString() throws {
        let h = try decode(#"{"position": "3"}"#)
        XCTAssertEqual(h.position?.intValue, 3)
        XCTAssertEqual(h.position?.stringValue, "3")
    }

    func testDecodesADoubleAndDropsTheTrailingZero() throws {
        let h = try decode(#"{"position": 25.0}"#)
        XCTAssertEqual(h.position?.intValue, 25)
        XCTAssertEqual(h.position?.numberLabel, "25")
    }

    func testKeepsAGenuineFraction() throws {
        let h = try decode(#"{"position": 12.5}"#)
        XCTAssertEqual(h.position?.numberLabel, "12.5")
    }

    func testDecodesNull() throws {
        // `position` is optional, so Swift's decoder resolves a JSON null to
        // `nil` before JSONValue's initialiser runs. The `.null` case remains
        // reachable for a present-but-unparsable element.
        let h = try decode(#"{"position": null}"#)
        XCTAssertNil(h.position)
    }

    func testANonNumericStringDoesNotBecomeANumber() throws {
        // "R" (retired) must stay a string, not silently coerce to 0.
        let h = try decode(#"{"position": "R"}"#)
        XCTAssertEqual(h.position?.stringValue, "R")
        XCTAssertNil(h.position?.intValue)
    }

    func testAMissingKeyIsNilRatherThanAFailure() throws {
        let h = try decode(#"{}"#)
        XCTAssertNil(h.position)
    }

    func testABooleanRoundTrips() throws {
        let h = try decode(#"{"position": true}"#)
        XCTAssertEqual(h.position?.stringValue, "true")
    }

    func testAFloatStringConvertsToInt() throws {
        // Ergast has been seen sending "25.0" where FastF1 sends 25.
        let h = try decode(#"{"position": "25.0"}"#)
        XCTAssertEqual(h.position?.intValue, 25)
        XCTAssertEqual(h.position?.doubleValue, 25.0)
    }

    func testEncodingRoundTripsThroughTheSameCase() throws {
        let original = try decode(#"{"position": "R"}"#)
        let reencoded = try JSONEncoder().encode(original)
        let again = try JSONDecoder().decode(Holder.self, from: reencoded)
        XCTAssertEqual(again.position?.stringValue, "R")
    }
}

/// Swift counterpart of the Android build's `FlagPeriodTest`.
///
/// Lap-range containment drives the lap-times chart bands and the telemetry
/// "Safety Car (lap 20)" badge, so it must be inclusive at both ends and agree
/// with the Kotlin `FlagPeriodDto.contains` behaviour exactly.
final class FlagPeriodTests: XCTestCase {

    private let doubleYellow = FlagPeriod(
        type: "DOUBLE_YELLOW",
        startLap: 17,
        endLap: 19,
        reason: "DOUBLE YELLOW FLAG IN TRACK SECTOR 5"
    )
    private let safetyCar = FlagPeriod(
        type: "SC",
        startLap: 20,
        endLap: 23,
        reason: "SAFETY CAR DEPLOYED"
    )
    private var periods: [FlagPeriod] { [doubleYellow, safetyCar] }

    func testALapAtTheStartOfAPeriodIsContained() {
        XCTAssertTrue(doubleYellow.contains(lap: 17))
    }

    func testALapAtTheEndOfAPeriodIsContained() {
        XCTAssertTrue(doubleYellow.contains(lap: 19))
    }

    func testALapOutsideTheRangeIsNotContained() {
        XCTAssertFalse(doubleYellow.contains(lap: 16))
        XCTAssertFalse(doubleYellow.contains(lap: 20))
    }

    func testASingleLapPeriodContainsOnlyThatLap() {
        let red = FlagPeriod(type: "RED", startLap: 30, endLap: 30, reason: nil)
        XCTAssertTrue(red.contains(lap: 30))
        XCTAssertFalse(red.contains(lap: 29))
        XCTAssertFalse(red.contains(lap: 31))
    }

    func testFirstMatchingPeriodIsFoundInAList() {
        XCTAssertEqual(periods.first { $0.contains(lap: 21) }, safetyCar)
        XCTAssertEqual(periods.first { $0.contains(lap: 17) }, doubleYellow)
    }

    func testNoPeriodMatchesAGreenFlagLap() {
        XCTAssertNil(periods.first { $0.contains(lap: 5) })
    }

    func testAdjacentPeriodsDoNotOverlap() {
        // 19 ends the double-yellow and 20 starts the safety car; neither lap
        // may fall into both, or a chart would band it twice.
        XCTAssertFalse(safetyCar.contains(lap: 19))
        XCTAssertFalse(doubleYellow.contains(lap: 20))
    }

    func testDecodesFromTheBackendPayloadShape() throws {
        let json = #"{"type": "VSC", "startLap": 12, "endLap": 14, "reason": "VSC DEPLOYED"}"#
        let period = try JSONDecoder().decode(FlagPeriod.self, from: Data(json.utf8))
        XCTAssertEqual(period.type, "VSC")
        XCTAssertTrue(period.contains(lap: 13))
    }
}
