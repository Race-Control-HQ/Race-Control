import SwiftUI

/// Grid of analysis entry-points shown on the race detail screen. Turns a race
/// into a hub: replay, telemetry, strategy, lap times, qualifying, weather,
/// retirements, flags, the full race-control log and the track map.
struct RaceAnalysisGrid: View {
    let event: RaceEvent

    private var columns: [GridItem] {
        [GridItem(.flexible(), spacing: Theme.Space.sm),
         GridItem(.flexible(), spacing: Theme.Space.sm),
         GridItem(.flexible(), spacing: Theme.Space.sm)]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            Text("ANALYSIS")
                .font(.caption.weight(.bold)).tracking(1)
                .foregroundStyle(Theme.Palette.textSecondary)

            LazyVGrid(columns: columns, spacing: Theme.Space.sm) {
                tile("Replay", "play.circle.fill", Theme.Palette.racingRed) {
                    ReplayView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Telemetry", "waveform.path.ecg", Theme.Palette.info) {
                    TelemetryView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Brake/Throttle", "flame.fill", Theme.Palette.racingRed) {
                    BrakeThrottleMapView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Lap Times", "chart.xyaxis.line", Theme.Palette.positive) {
                    LapTimesChartView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Race Trace", "chart.line.uptrend.xyaxis", Theme.Palette.racingRed) {
                    RaceTraceView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Positions", "arrow.up.arrow.down", Theme.Palette.info) {
                    PositionChartView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Strategy", "timeline.selection", Theme.Palette.warning) {
                    StrategyView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Tyre Degradation", "circle.dashed", Theme.Palette.warning) {
                    TyrePerformanceView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Pit Stops", "wrench.and.screwdriver", Theme.Palette.racingRed) {
                    PitStopsView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Pit Swimlane", "arrow.triangle.branch", Theme.Palette.info) {
                    PitSwimlaneView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Sector Waterfall", "chart.bar.xaxis", Theme.Palette.info) {
                    QualifyingSectorsView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Speed Trap", "speedometer", Theme.Palette.positive) {
                    SpeedTrapView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Mini-Sectors", "map.fill", Theme.Palette.positive) {
                    MiniSectorsView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Qualifying", "stopwatch.fill", Theme.Palette.info) {
                    QualifyingView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Qualifying Ladder", "chart.line.downtrend.xyaxis", Theme.Palette.warning) {
                    QualifyingLadderView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Track Map", "map.fill", Theme.Palette.textSecondary) {
                    CircuitMapView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Corner Speeds", "chart.bar.fill", Theme.Palette.positive) {
                    CornerSpeedProfileView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Weather", "cloud.sun.fill", Theme.Palette.info) {
                    WeatherView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Retirements", "exclamationmark.triangle.fill", Theme.Palette.negative) {
                    RetirementsView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Flags", "flag.checkered", Theme.Palette.flagYellow) {
                    FlagsView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Penalties", "exclamationmark.octagon.fill", Theme.Palette.negative) {
                    PenaltiesView(year: event.year, round: event.round, title: event.displayName)
                }
                tile("Race Control", "list.bullet.clipboard.fill", Theme.Palette.info) {
                    RaceControlView(year: event.year, round: event.round, title: event.displayName)
                }
            }
        }
    }

    private func tile<Destination: View>(_ label: String, _ icon: String, _ tint: Color,
                                         @ViewBuilder destination: @escaping () -> Destination) -> some View {
        NavigationLink {
            destination()
        } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.title2)
                Text(label).font(.caption.weight(.semibold))
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, minHeight: 72)
            .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: Theme.Radius.md))
        }
        .buttonStyle(.plain)
    }
}
