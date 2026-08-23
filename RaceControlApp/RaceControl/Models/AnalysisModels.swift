import Foundation

// MARK: - Race trace

struct RaceTraceResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let available: Bool
    let mode: String
    let totalLaps: Int
    let greenFlagMedianLapMs: Int?
    let yDomainMs: [Int]?
    let periods: [FlagPeriod]
    let drivers: [RaceTraceDriver]
}

struct RaceTraceDriver: Codable, Identifiable, Hashable {
    let code: String
    let driverId: String?
    let fullName: String?
    let teamName: String?
    let teamColor: String?
    let finishPosition: Int?
    let retired: Bool
    let status: String?
    let lapsCompleted: Int
    let laps: [RaceTraceLap]
    var id: String { code }
}

struct RaceTraceLap: Codable, Hashable {
    let lap: Int
    let deltaMs: Int
    let cumulativeMs: Int
    let lapTimeMs: Int?
    let compound: String?
}

// MARK: - Tyre degradation

struct TyrePerformanceResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let available: Bool
    let xDomain: [Int]?
    let yDomainMs: [Int]?
    let compoundBaselines: [CompoundBaseline]
    let stints: [TyrePerformanceStint]
}

struct CompoundBaseline: Codable, Identifiable, Hashable {
    let compound: String
    let slopeSecPerLap: Double
    let stintCount: Int
    var id: String { compound }
}

struct TyrePerformanceStint: Codable, Identifiable, Hashable {
    let id: String
    let driverCode: String
    let driverId: String?
    let fullName: String?
    let teamName: String?
    let teamColor: String?
    let stint: Int
    let compound: String?
    let freshTyre: Bool?
    let startLap: Int
    let endLap: Int
    let bestLapMs: Int
    let slopeSecPerLap: Double
    let points: [TyrePerformancePoint]
    let fit: [TyrePerformanceFitPoint]
}

struct TyrePerformancePoint: Codable, Hashable {
    let lap: Int
    let tyreLife: Double
    let lapTimeMs: Int
    let deltaMs: Int
}

struct TyrePerformanceFitPoint: Codable, Hashable {
    let tyreLife: Double
    let deltaMs: Int
}

// MARK: - Pit-stop ledger

struct PitStopsResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let available: Bool
    let circuitMedianLossMs: Int?
    let lossDomainMs: [Int]?
    let stops: [PitStopLedgerItem]
}

struct PitStopLedgerItem: Codable, Identifiable, Hashable {
    let id: String
    let driverCode: String
    let driverId: String?
    let fullName: String?
    let teamName: String?
    let teamColor: String?
    let stop: Int
    let lap: Int
    let compoundIn: String?
    let compoundOut: String?
    let lossMs: Int
    let deltaToMedianMs: Int
    let entryPosition: Int?
    let rejoinPosition: Int?
    let positionsGained: Int?
    let outcome: String
    let rivals: [String]
}

// MARK: - Qualifying sector waterfall

struct QualifyingSectorsResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let available: Bool
    let poleCode: String?
    let poleLapMs: Int?
    let gapDomainMs: [Int]?
    let drivers: [QualifyingSectorDriver]
}

struct QualifyingSectorDriver: Codable, Identifiable, Hashable {
    let code: String
    let driverId: String?
    let fullName: String?
    let teamName: String?
    let teamColor: String?
    let lapMs: Int
    let gapToPoleMs: Int
    let sectorMs: [Int]
    let sectorDeltaMs: [Int]
    let idealSectorMs: [Int]
    let idealLapMs: Int
    let idealGainMs: Int
    let speedI1: Double?
    let speedI2: Double?
    let speedFL: Double?
    let speedST: Double?
    var id: String { code }
}

// MARK: - Lap-time evolution

struct LapTimesResponse: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let totalLaps: Int
    let drivers: [LapTimeDriver]
}

struct LapTimeDriver: Codable, Identifiable, Hashable {
    let code: String
    let driverId: String?
    let teamName: String?
    let teamColor: String?
    let laps: [LapTimePoint]
    var id: String { code }
}

struct LapTimePoint: Codable, Hashable {
    let lap: Int
    let timeMs: Int
    let compound: String?
    var seconds: Double { Double(timeMs) / 1000 }
}

// MARK: - Tyre strategy

struct StrategyResponse: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let totalLaps: Int
    let drivers: [StrategyDriver]
}

struct StrategyDriver: Codable, Identifiable, Hashable {
    let code: String
    let driverId: String?
    let teamName: String?
    let teamColor: String?
    let pitStops: Int
    let stints: [Stint]
    let status: String?
    let retired: Bool
    var id: String { code }
}

struct Stint: Codable, Hashable, Identifiable {
    let stint: Int
    let compound: String?
    let startLap: Int
    let endLap: Int
    let laps: Int
    var id: Int { stint }
}

// MARK: - Weather

struct WeatherResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let available: Bool
    let airTemp: Double?
    let trackTemp: Double?
    let humidity: Double?
    let pressure: Double?
    let windSpeed: Double?
    let rainfall: Bool?
    let airTempMax: Double?
    let trackTempMax: Double?
    let timeline: [WeatherSample]?
}

struct WeatherSample: Codable, Identifiable, Hashable {
    let timeSeconds: Double
    let airTemp: Double?
    let trackTemp: Double?
    let humidity: Double?
    let pressure: Double?
    let windSpeed: Double?
    let rainfall: Bool
    var id: Double { timeSeconds }
}

// MARK: - Mini-sector dominance

struct MiniSectorsResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let available: Bool
    let driverCount: Int
    let segmentCount: Int
    let outlineSourceCode: String?
    let legend: [MiniSectorLegend]
    let segments: [MiniSectorSegment]
}

struct MiniSectorLegend: Codable, Identifiable, Hashable {
    let code: String
    let teamColor: String?
    let segmentsWon: Int
    var id: String { code }
}

struct MiniSectorSegment: Codable, Identifiable, Hashable {
    let index: Int
    let startDistance: Double
    let endDistance: Double
    let points: [[Double]]
    let winnerCode: String
    let teamColor: String?
    let timeMs: Int
    let gapMs: Int
    var id: Int { index }
}

// MARK: - Title scenarios

struct TitleScenariosResponse: Codable {
    let year: Int
    let throughRound: Int?
    let available: Bool
    let roundsRemaining: Int
    let positions: [Int]
    let drivers: [TitleScenarioDriver]
    let cells: [TitleScenarioCell]
    let clinchText: String?
    let summary: String?
}

struct TitleScenarioDriver: Codable, Identifiable, Hashable {
    let driverId: String
    let code: String
    let teamColor: String?
    let points: Double
    var id: String { driverId }
}

struct TitleScenarioCell: Codable, Identifiable, Hashable {
    let d1Position: Int
    let d2Position: Int
    let d1Points: Double
    let d2Points: Double
    let margin: Double
    let outcome: String
    var id: String { "\(d1Position)-\(d2Position)" }
}

// MARK: - Driver fingerprint

struct DriverFingerprintResponse: Codable {
    let year: Int
    let driverId: String
    let available: Bool
    let axes: [FingerprintAxis]
}

struct FingerprintAxis: Codable, Identifiable, Hashable {
    let key: String
    let label: String
    let percentile: Int
    let rawValue: Double
    var id: String { key }
}

// MARK: - Telemetry

struct TelemetryResponse: Codable {
    let year: Int
    let round: Int
    let available: Bool
    let eventName: String?
    let trace: TelemetryTrace?
}

struct TelemetryCompareResponse: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let available: Bool
    let traces: [TelemetryTrace]
}

struct TelemetryTrace: Codable, Identifiable, Hashable {
    let code: String
    let driverName: String?
    let teamName: String?
    let teamColor: String?
    let lapNumber: Int?
    let lapTime: String?
    let lapTimeMs: Int?
    let compound: String?
    let distance: [Double]
    let time: [Double]?
    let speed: [Double]
    let throttle: [Double]
    let brake: [Int]
    let gear: [Int]
    let rpm: [Int]
    let drs: [Int]
    let x: [Double]
    let y: [Double]
    var id: String { code }

    /// Zip distance + a channel into chartable points, capped for performance.
    func series(_ channel: [Double]) -> [TelemetrySample] {
        zip(distance, channel).map { TelemetrySample(distance: $0.0, value: $0.1) }
    }
}

struct TelemetrySample: Identifiable, Hashable {
    let distance: Double
    let value: Double
    var id: Double { distance }
}

// MARK: - Race drivers (picker)

struct RaceDriver: Codable, Identifiable, Hashable {
    let code: String?
    let driverId: String?
    let fullName: String?
    let teamName: String?
    let teamColor: String?
    let number: String?
    var id: String { code ?? driverId ?? UUID().uuidString }
}

// MARK: - Retirements

struct RetirementsResponse: Codable {
    let year: Int
    let round: Int
    let eventName: String?
    let retirements: [Retirement]
}

struct Retirement: Codable, Identifiable, Hashable {
    let driver: String?
    let fullName: String?
    let driverId: String?
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let status: String?
    let classifiedPosition: String?
    let lapsCompleted: Int?
    var id: String { driver ?? driverId ?? UUID().uuidString }
}

// MARK: - Flags / Safety Car

struct FlagsResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let totalLaps: Int
    let events: [FlagEvent]
    let periods: [FlagPeriod]
}

/// A single race-control message from the raw timeline.
struct FlagEvent: Codable, Identifiable, Hashable {
    let time: String?
    let lap: Int?
    let category: String?
    let flag: String?
    let status: String?
    let scope: String?
    let sector: Int?
    let driverNumber: String?
    let driverCode: String?
    let message: String?

    var id: String { "\(time ?? "-")|\(lap ?? -1)|\(category ?? "")|\(flag ?? "")|\(message ?? "")" }
}

/// A collapsed lap range during which a flag/safety-car condition was active:
/// used to band the lap-based charts (`YELLOW`, `DOUBLE_YELLOW`, `RED`, `SC`, `VSC`).
struct FlagPeriod: Codable, Identifiable, Hashable {
    let type: String
    let startLap: Int
    let endLap: Int
    let reason: String?

    var id: String { "\(type)-\(startLap)-\(endLap)" }

    /// True when `lap` falls within this period's inclusive lap range.
    func contains(lap: Int) -> Bool { lap >= startLap && lap <= endLap }
}

// MARK: - Race Control (full, unfiltered message log)

struct RaceControlResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let totalLaps: Int
    let messages: [RaceControlMessage]
}

/// A single race-control message from the complete, unfiltered timeline:
/// flags, safety-car, DRS, car events and "other" (investigations, penalties).
struct RaceControlMessage: Codable, Identifiable, Hashable {
    let time: String?
    let lap: Int?
    let category: String?
    let flag: String?
    let status: String?
    let scope: String?
    let sector: Int?
    let driverNumber: String?
    let driverCode: String?
    let message: String?

    var id: String { "\(time ?? "-")|\(lap ?? -1)|\(category ?? "")|\(flag ?? "")|\(message ?? "")" }
}

// MARK: - Penalties

struct PenaltiesResponse: Codable {
    let year: Int
    let round: Int
    let session: String
    let eventName: String?
    let penalties: [Penalty]
}

/// A single stewards' penalty decision, parsed from the race-control log.
struct Penalty: Codable, Identifiable, Hashable {
    let time: String?
    let lap: Int?
    let type: String
    let value: String?
    let reason: String?
    let message: String?
    let driverCode: String?
    let driverId: String?
    let driverName: String?
    let teamName: String?
    let teamLogoUrl: String?
    let teamColor: String?

    var id: String { "\(time ?? "")-\(driverCode ?? "")-\(type)" }
}

// MARK: - Reliability

struct ReliabilityResponse: Codable {
    let year: Int
    let races: Int
    let drivers: [ReliabilityDriver]
    let teams: [ReliabilityTeam]
}

struct ReliabilityDriver: Codable, Identifiable, Hashable {
    let driverId: String
    let name: String
    let teamId: String?
    let finished: Int
    let mechanical: Int
    let accident: Int
    let disqualified: Int
    let other: Int
    let dnf: Int
    let starts: Int
    let finishRate: Double
    var id: String { driverId }
}

struct ReliabilityTeam: Codable, Identifiable, Hashable {
    let teamId: String
    let teamName: String?
    let teamLogoUrl: String?
    let finished: Int
    let mechanical: Int
    let accident: Int
    let disqualified: Int
    let other: Int
    let dnf: Int
    let starts: Int
    let finishRate: Double
    var id: String { teamId }
}

// MARK: - Standings evolution

struct StandingsEvolution: Codable {
    let year: Int
    let rounds: [Int]
    let drivers: [EvolutionDriver]
}

struct EvolutionDriver: Codable, Identifiable, Hashable {
    let driverId: String
    let name: String
    let code: String?
    let teamName: String?
    let teamColor: String?
    let points: Double
    let series: [EvolutionPoint]
    var id: String { driverId }
}

struct EvolutionPoint: Codable, Hashable {
    let round: Int
    let points: Double
}

// MARK: - WDC calculator

struct WdcCalculator: Codable {
    let year: Int
    let roundsRemaining: Int
    let sprintRoundsRemaining: Int
    let maxRemainingPoints: Int
    let leaderPoints: Double
    let decided: Bool
    let drivers: [WdcDriverEntry]
    let throughRound: Int?
    let roundsInSeason: Int
    /// Optional footnotes describing known scoring approximations (e.g. no
    /// countback tie-break, flat modern sprint-points assumption applied to
    /// historical seasons). Absent on older backends, so it decodes to nil
    /// rather than failing.
    let notes: [String]?
}

struct WdcDriverEntry: Codable, Identifiable {
    let position: Int?
    let driverId: String?
    let driverCode: String?
    let givenName: String?
    let familyName: String?
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?
    let teamColor: String?
    let headshotUrl: String?
    let points: Double
    let maxPoints: Double
    let pointsBehindLeader: Double
    let canWin: Bool

    var id: String { driverId ?? driverCode ?? "\(position ?? 0)" }
    var fullName: String { [givenName, familyName].compactMap { $0 }.joined(separator: " ") }
}

// MARK: - Head-to-head compare

struct CompareResponse: Codable {
    let year: Int
    let drivers: [CompareDriver]
}

struct CompareDriver: Codable, Identifiable, Hashable {
    let driverId: String
    let name: String
    let teamName: String?
    let teamId: String?
    let teamLogoUrl: String?
    let points: Double
    let wins: Int
    let podiums: Int
    let poles: Int
    let bestFinish: Int?
    let dnf: Int
    let raceWinsH2h: Int
    let qualWinsH2h: Int

    enum CodingKeys: String, CodingKey {
        case driverId, name, teamName, teamId, teamLogoUrl, points, wins, podiums, poles, bestFinish, dnf
        case raceWinsH2h = "raceWins_h2h"
        case qualWinsH2h = "qualWins_h2h"
    }
    var id: String { driverId }
    var pointsLabel: String {
        points.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(points)) : String(points)
    }
}
