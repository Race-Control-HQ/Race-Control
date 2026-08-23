// Response shapes for backend/fastf1_service.py, mirrored 1:1 for the web BFF.
// Field names match the backend's JSON output exactly (camelCase already).

export interface EventSession {
  name: string;
  date: string | null;
  identifier: string; // FP1, FP2, FP3, Q, S, SQ, SS, R
}

export interface ScheduleEvent {
  round: number;
  name: string;
  officialName: string | null;
  country: string | null;
  location: string | null;
  date: string | null;
  format: string | null;
  sessions: EventSession[];
  completed: boolean;
  year: number;
}

export interface ResultRow {
  position: number | null;
  classifiedPosition: string | null;
  driverNumber: string | null;
  abbreviation: string | null;
  driverId: string | null;
  firstName: string | null;
  lastName: string | null;
  fullName: string | null;
  headshotUrl: string | null;
  countryCode: string | null;
  teamName: string | null;
  teamId: string | null;
  teamLogoUrl: string | null;
  teamColor: string | null;
  gridPosition: number | null;
  status: string | null;
  points: number | null;
  timeMs: number | null;
  q1: string | null;
  q2: string | null;
  q3: string | null;
  q1Gap: string | null;
  q2Gap: string | null;
  q3Gap: string | null;
  /** Practice only: best lap of the session, its gap to the fastest, and
      how many laps the driver ran. Null for every other session. */
  bestLap: string | null;
  bestLapGap: string | null;
  lapsCompleted: number | null;
}

export interface SessionResults {
  year: number;
  round: number;
  session: string;
  sessionName: string;
  eventName: string;
  totalLaps: number | null;
  results: ResultRow[];
}

export interface DriverStanding {
  position: number | null;
  points: number | null;
  wins: number | null;
  driverId: string;
  driverNumber: string | null;
  driverCode: string | null;
  givenName: string | null;
  familyName: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  teamName: string | null;
  teamId: string | null;
  teamLogoUrl: string | null;
}

export interface ConstructorStanding {
  position: number | null;
  points: number | null;
  wins: number | null;
  teamId: string;
  teamName: string | null;
  nationality: string | null;
  teamLogoUrl: string | null;
}

export interface Driver {
  driverId: string;
  givenName: string | null;
  familyName: string | null;
  code: string | null;
  number: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  teamName: string | null;
  teamId: string | null;
  teamLogoUrl: string | null;
  teamColor: string | null;
  headshotUrl: string | null;
  countryCode: string | null;
  position: number | null;
  points: number | null;
  wins: number | null;
}

export interface DriverSeasonResult {
  round: number | null;
  raceName: string | null;
  position: number | null;
  points: number | null;
  grid: number | null;
  status: string | null;
}

export interface DriverDetail extends Driver {
  seasonResults: DriverSeasonResult[];
}

export interface TeamRosterEntry {
  driverId: string;
  name: string;
  code: string | null;
  number: string | null;
  headshotUrl: string | null;
  points: number | null;
}

export interface Team extends ConstructorStanding {
  teamColor: string | null;
  drivers: TeamRosterEntry[];
}

export interface Circuit {
  circuitId: string;
  name: string | null;
  locality: string | null;
  country: string | null;
  lat: number | null;
  long: number | null;
}

export interface CircuitMapPoint {
  x: number;
  y: number;
  z: number;
  speed: number;
  drs: number;
  distance: number;
}

export interface CircuitCorner {
  number: number;
  letter: string;
  x: number;
  y: number;
  /** Turn angle in degrees, when FastF1 provides it. */
  angle: number | null;
  /** Distance along the lap (metres) at this corner. */
  distanceMeters: number | null;
  /** Approximate speed (km/h) at this corner, cross-referenced from the fastest lap's trace. */
  speed: number | null;
}

export interface CircuitMarker {
  number: number;
  x: number;
  y: number;
}

export interface CircuitMap {
  year: number;
  round: number;
  eventName: string | null;
  location: string | null;
  country: string | null;
  outline: { x: number; y: number }[];
  points: CircuitMapPoint[];
  corners: CircuitCorner[];
  marshalLights: CircuitMarker[];
  marshalSectors: CircuitMarker[];
  rotation: number;
  lengthMeters: number | null;
  minElevation: number | null;
  maxElevation: number | null;
  /** Distinct position samples behind the outline; low values (vs. hundreds)
   * mean the source telemetry was too coarse to draw smooth corners. */
  outlineSamples?: number;
  fastestLap: {
    driver: string | null;
    driverName: string | null;
    team: string | null;
    teamLogoUrl: string | null;
    teamColor: string | null;
    time: string | null;
    compound: string | null;
  } | null;
}

export interface ReplayEntry {
  position: number;
  driver: string;
  driverId: string | null;
  teamColor: string | null;
  teamName: string | null;
  teamLogoUrl: string | null;
  lapTimeMs: number | null;
  lapTime: string | null;
  compound: string | null;
  tyreLife: number | null;
  gapMs?: number | null;
  gap?: string | null;
}

export interface ReplayFrame {
  lap: number;
  order: ReplayEntry[];
}

export interface RaceReplay {
  year: number;
  round: number;
  eventName: string | null;
  totalLaps: number;
  drivers: { code: string; driverId: string | null; fullName: string | null; teamName: string | null; teamLogoUrl: string | null; teamColor: string | null; number: string | null }[];
  frames: ReplayFrame[];
}

export interface ReplayLapPositions {
  lap: number;
  /** Driver code -> a handful of [x, y] samples spanning that lap, in order. */
  positions: Record<string, [number, number][]>;
}

export interface ReplayPositions {
  year: number;
  round: number;
  eventName: string | null;
  totalLaps: number;
  drivers: { code: string; driverId: string | null; fullName: string | null; teamName: string | null; teamLogoUrl: string | null; teamColor: string | null; number: string | null }[];
  laps: ReplayLapPositions[];
}

export interface LapTimeEntry {
  lap: number;
  timeMs: number;
  compound: string | null;
}

export interface DriverLapTimes {
  code: string;
  driverId: string | null;
  teamName: string | null;
  teamColor: string | null;
  laps: LapTimeEntry[];
}

export interface LapTimesResponse {
  year: number;
  round: number;
  eventName: string;
  totalLaps: number;
  drivers: DriverLapTimes[];
}

export interface StrategyStint {
  stint: number;
  compound: string | null;
  startLap: number;
  endLap: number;
  laps: number;
}

export interface DriverStrategy {
  code: string;
  driverId: string | null;
  teamName: string | null;
  teamColor: string | null;
  pitStops: number;
  stints: StrategyStint[];
  status: string | null;
  retired: boolean;
}

export interface StrategyResponse {
  year: number;
  round: number;
  eventName: string;
  totalLaps: number;
  drivers: DriverStrategy[];
}

export interface WeatherResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  available: boolean;
  airTemp?: number | null;
  trackTemp?: number | null;
  humidity?: number | null;
  pressure?: number | null;
  windSpeed?: number | null;
  rainfall?: boolean;
  airTempMax?: number | null;
  trackTempMax?: number | null;
  timeline?: WeatherSample[];
}

export interface WeatherSample {
  timeSeconds: number;
  airTemp: number | null;
  trackTemp: number | null;
  humidity: number | null;
  pressure: number | null;
  windSpeed: number | null;
  rainfall: boolean;
}

export interface RaceDriver {
  code: string;
  driverId: string | null;
  fullName: string | null;
  teamName: string | null;
  teamColor: string | null;
  number: string | null;
}

export interface TelemetryTrace {
  code: string;
  lapNumber: number | null;
  lapTime: string | null;
  lapTimeMs: number | null;
  compound: string | null;
  distance: number[];
  time: number[];
  speed: number[];
  throttle: number[];
  brake: number[];
  gear: number[];
  rpm: number[];
  drs: number[];
  x: number[];
  y: number[];
  driverName?: string | null;
  teamName?: string | null;
  teamColor?: string | null;
}

export interface TelemetryResponse {
  year: number;
  round: number;
  available: boolean;
  driver?: string;
  eventName?: string | null;
  trace?: TelemetryTrace;
}

export interface TelemetryCompareResponse {
  year: number;
  round: number;
  eventName: string | null;
  available: boolean;
  traces: TelemetryTrace[];
}

export interface Retirement {
  driver: string | null;
  fullName: string | null;
  driverId: string | null;
  teamName: string | null;
  teamId: string | null;
  teamLogoUrl: string | null;
  teamColor: string | null;
  status: string;
  classifiedPosition: string | null;
  lapsCompleted: number | null;
}

export interface RetirementsResponse {
  year: number;
  round: number;
  eventName: string | null;
  retirements: Retirement[];
}

export interface ReliabilityEntry {
  driverId?: string;
  teamId?: string;
  name?: string;
  teamName?: string;
  teamLogoUrl?: string | null;
  finished: number;
  mechanical: number;
  accident: number;
  disqualified: number;
  other: number;
  dnf: number;
  starts: number;
  finishRate: number;
}

export interface ReliabilityResponse {
  year: number;
  races: number;
  drivers: ReliabilityEntry[];
  teams: ReliabilityEntry[];
}

export interface CompareRound {
  round: number | null;
  raceName: string | null;
  position: number | null;
}

export interface CompareDriver {
  driverId: string;
  name: string;
  teamName: string | null;
  teamId: string | null;
  teamLogoUrl: string | null;
  points: number;
  wins: number;
  podiums: number;
  poles: number;
  bestFinish: number | null;
  dnf: number;
  raceWins_h2h: number;
  qualWins_h2h: number;
  rounds: CompareRound[];
}

export interface CompareResponse {
  year: number;
  drivers: [CompareDriver, CompareDriver];
}

export interface StandingsEvolutionPoint {
  round: number;
  points: number;
}

export interface StandingsEvolutionDriver {
  driverId: string;
  name: string;
  code: string | null;
  teamName: string | null;
  teamColor: string | null;
  points: number;
  series: StandingsEvolutionPoint[];
}

export interface StandingsEvolutionResponse {
  year: number;
  rounds: number[];
  drivers: StandingsEvolutionDriver[];
}

export interface FlagEvent {
  time: string | null;
  lap: number | null;
  category: string | null;
  flag: string | null;
  status: string | null;
  scope: string | null;
  sector: number | null;
  driverNumber: string | null;
  driverCode: string | null;
  message: string | null;
}

export type FlagPeriodType = "YELLOW" | "DOUBLE_YELLOW" | "RED" | "SC" | "VSC";

export interface FlagPeriod {
  type: FlagPeriodType;
  startLap: number;
  endLap: number;
  reason: string | null;
}

export interface FlagsResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  totalLaps: number;
  events: FlagEvent[];
  periods: FlagPeriod[];
}

export type RaceControlCategory = "Flag" | "SafetyCar" | "Drs" | "CarEvent" | "Other";

export interface RaceControlMessage {
  time: string | null;
  lap: number | null;
  category: RaceControlCategory;
  flag: string | null;
  status: string | null;
  scope: string | null;
  sector: number | null;
  driverNumber: string | null;
  driverCode: string | null;
  message: string | null;
}

export interface RaceControlResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  totalLaps: number | null;
  messages: RaceControlMessage[];
}

export type PenaltyType =
  | "Time Penalty"
  | "Stop & Go Penalty"
  | "Drive Through Penalty"
  | "Grid Penalty"
  | "Reprimand"
  | "Disqualification";

export interface Penalty {
  time: string | null;
  lap: number | null;
  type: PenaltyType;
  value: string | null;
  reason: string | null;
  message: string | null;
  driverCode: string | null;
  driverId: string | null;
  driverName: string | null;
  teamName: string | null;
  teamLogoUrl: string | null;
  teamColor: string | null;
}

export interface PenaltiesResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  penalties: Penalty[];
}

export interface WdcDriver {
  position: number | null;
  driverId: string | null;
  driverCode: string | null;
  givenName: string | null;
  familyName: string | null;
  teamName: string | null;
  teamId: string | null;
  teamLogoUrl: string | null;
  teamColor: string | null;
  headshotUrl: string | null;
  points: number;
  maxPoints: number;
  pointsBehindLeader: number;
  canWin: boolean;
}

export interface WdcCalculator {
  year: number;
  /** Round the snapshot is "as of"; null means the live, current-day view. */
  throughRound: number | null;
  /** Total rounds on the season's calendar, for building a round picker. */
  roundsInSeason: number;
  roundsRemaining: number;
  sprintRoundsRemaining: number;
  maxRemainingPoints: number;
  leaderPoints: number;
  /** True once only the current leader retains a mathematical path to the title. */
  decided: boolean;
  drivers: WdcDriver[];
}

// MARK: - Race trace (/api/race-trace)

export type RaceTraceMode = "median" | "leader";

export interface RaceTraceLap {
  lap: number;
  /**
   * Gap to the lap's shared baseline. **Higher is further ahead**, in both
   * modes: `median` measures against the fixed green-flag reference pace, `leader`
   * against whoever completed it first. Because the baseline is shared, the
   * difference between two drivers' `deltaMs` is the real gap between them.
   */
  deltaMs: number;
  /** Elapsed race time at the end of this lap. */
  cumulativeMs: number;
  lapTimeMs: number | null;
  compound: string | null;
}

export interface RaceTraceDriver {
  code: string;
  driverId: string | null;
  fullName: string | null;
  teamName: string | null;
  teamColor: string | null;
  finishPosition: number | null;
  retired: boolean;
  status: string | null;
  /** Last lap this driver has data for; a retirement stops short of totalLaps. */
  lapsCompleted: number;
  laps: RaceTraceLap[];
}

export interface RaceTraceResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  available: boolean;
  mode: RaceTraceMode;
  totalLaps: number;
  /**
   * Median green-flag lap used to de-trend cumulative race time.
   */
  greenFlagMedianLapMs: number | null;
  /** Padded y-axis domain, computed server-side so all clients agree. */
  yDomainMs: [number, number] | null;
  periods: FlagPeriod[];
  /** Finishing order, so taking the first N gives the leaders. */
  drivers: RaceTraceDriver[];
}

// MARK: - Tyre performance (/api/tyre-performance)

export interface TyrePerformancePoint {
  lap: number;
  tyreLife: number;
  lapTimeMs: number;
  deltaMs: number;
}

export interface TyrePerformanceStint {
  id: string;
  driverCode: string;
  driverId: string | null;
  fullName: string | null;
  teamName: string | null;
  teamColor: string | null;
  stint: number;
  compound: string | null;
  freshTyre: boolean | null;
  startLap: number;
  endLap: number;
  bestLapMs: number;
  slopeSecPerLap: number;
  points: TyrePerformancePoint[];
  fit: { tyreLife: number; deltaMs: number }[];
}

export interface TyrePerformanceResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  available: boolean;
  xDomain: [number, number] | null;
  yDomainMs: [number, number] | null;
  compoundBaselines: {
    compound: string;
    slopeSecPerLap: number;
    stintCount: number;
  }[];
  stints: TyrePerformanceStint[];
}

// MARK: - Pit-stop ledger (/api/pit-stops)

export interface PitStopLedgerItem {
  id: string;
  driverCode: string;
  driverId: string | null;
  fullName: string | null;
  teamName: string | null;
  teamColor: string | null;
  stop: number;
  lap: number;
  compoundIn: string | null;
  compoundOut: string | null;
  lossMs: number;
  deltaToMedianMs: number;
  entryPosition: number | null;
  rejoinPosition: number | null;
  positionsGained: number | null;
  outcome: "UNDERCUT" | "OVERCUT" | "HELD";
  rivals: string[];
}

export interface PitStopsResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  available: boolean;
  circuitMedianLossMs: number | null;
  lossDomainMs: [number, number] | null;
  stops: PitStopLedgerItem[];
}

// MARK: - Qualifying sector waterfall (/api/qualifying-sectors)

export interface QualifyingSectorDriver {
  code: string;
  driverId: string | null;
  fullName: string | null;
  teamName: string | null;
  teamColor: string | null;
  lapMs: number;
  gapToPoleMs: number;
  sectorMs: [number, number, number];
  sectorDeltaMs: [number, number, number];
  idealSectorMs: [number, number, number];
  idealLapMs: number;
  idealGainMs: number;
  speedI1: number | null;
  speedI2: number | null;
  speedFL: number | null;
  speedST: number | null;
}

export interface QualifyingSectorsResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  available: boolean;
  poleCode: string | null;
  poleLapMs: number | null;
  gapDomainMs: [number, number] | null;
  drivers: QualifyingSectorDriver[];
}

// MARK: - Mini-sector dominance (/api/minisectors)

export interface MiniSectorSegment {
  index: number;
  startDistance: number;
  endDistance: number;
  points: [number, number][];
  winnerCode: string;
  teamColor: string | null;
  timeMs: number;
  gapMs: number;
}

export interface MiniSectorsResponse {
  year: number;
  round: number;
  session: string;
  eventName: string | null;
  available: boolean;
  driverCount: number;
  segmentCount: number;
  outlineSourceCode?: string;
  legend: { code: string; teamColor: string | null; segmentsWon: number }[];
  segments: MiniSectorSegment[];
}

export interface TitleScenarioCell {
  d1Position: number;
  d2Position: number;
  d1Points: number;
  d2Points: number;
  margin: number;
  outcome: "D1_CLINCHED" | "D2_CLINCHED" | "D1_LEADS" | "D2_LEADS" | "TIED";
}

export interface TitleScenariosResponse {
  year: number;
  throughRound: number | null;
  available: boolean;
  roundsRemaining: number;
  positions: number[];
  drivers: { driverId: string; code: string; teamColor: string | null; points: number }[];
  cells: TitleScenarioCell[];
  clinchText: string | null;
  summary: string | null;
}

export interface DriverFingerprintResponse {
  year: number;
  driverId: string;
  available: boolean;
  driver: Driver | null;
  axes: { key: string; label: string; percentile: number; rawValue: number }[];
}
