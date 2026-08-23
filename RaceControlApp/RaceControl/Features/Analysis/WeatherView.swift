import SwiftUI
import Charts

/// Session weather conditions: temperatures, humidity, wind and rainfall.
struct WeatherView: View {
    let year: Int
    let round: Int
    let title: String

    @StateObject private var vm = WeatherViewModel()
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round)
        } content: { wx in
            if !wx.available {
                EmptyStateView(icon: "cloud.slash", title: "No Weather Data",
                               message: "Weather data isn't available for this session.")
            } else {
                content(wx)
            }
        }
        .navigationTitle("Weather")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }

    private func content(_ wx: WeatherResponse) -> some View {
        ScrollView {
            VStack(spacing: Theme.Space.md) {
                if wx.rainfall == true {
                    banner(icon: "cloud.rain.fill", text: "Wet session: rainfall recorded",
                           color: Theme.Palette.info)
                } else {
                    banner(icon: "sun.max.fill", text: "Dry session", color: Theme.Palette.warning)
                }
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())],
                          spacing: Theme.Space.md) {
                    metric("Air Temp", value: wx.airTemp, unit: "°C", icon: "thermometer.medium",
                           detail: wx.airTempMax.map { "max \(fmt($0))°" })
                    metric("Track Temp", value: wx.trackTemp, unit: "°C", icon: "road.lanes",
                           detail: wx.trackTempMax.map { "max \(fmt($0))°" })
                    metric("Humidity", value: wx.humidity, unit: "%", icon: "humidity.fill")
                    metric("Wind", value: wx.windSpeed, unit: " m/s", icon: "wind")
                    metric("Pressure", value: wx.pressure, unit: " mbar", icon: "gauge.medium")
                }
                if let timeline = wx.timeline, timeline.count > 1 {
                    Card {
                      VStack(alignment: .leading, spacing: Theme.Space.sm) {
                        // Two same-weight lines with no key was a guessing
                        // game; name them before the plot.
                        HStack(spacing: Theme.Space.md) {
                            seriesKey("Air", Theme.Palette.info)
                            seriesKey("Track", Theme.Palette.warning)
                            Spacer()
                            if wx.rainfall == true { seriesKey("Rain", Theme.Palette.info.opacity(0.35)) }
                        }
                        Chart(Array(timeline.enumerated()), id: \.element.id) { index, sample in
                            if sample.rainfall {
                                // Band to the next sample's timestamp rather than a fixed
                                // +60s, so it tiles without gaps or overlaps regardless of
                                // the session's actual weather-sampling interval.
                                let end = index + 1 < timeline.count ? timeline[index + 1].timeSeconds : sample.timeSeconds + 60
                                RectangleMark(
                                    xStart: .value("Start", sample.timeSeconds),
                                    xEnd: .value("End", end)
                                )
                                .foregroundStyle(Theme.Palette.info.opacity(0.12))
                            }
                            if let air = sample.airTemp {
                                LineMark(
                                    x: .value("Time", sample.timeSeconds),
                                    y: .value("Air", air),
                                    series: .value("Series", "Air")
                                )
                                .foregroundStyle(Theme.Palette.info)
                                .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                                 lineCap: .round, lineJoin: .round))
                            }
                            if let track = sample.trackTemp {
                                LineMark(
                                    x: .value("Time", sample.timeSeconds),
                                    y: .value("Track", track),
                                    series: .value("Series", "Track")
                                )
                                .foregroundStyle(Theme.Palette.warning)
                                .lineStyle(.init(lineWidth: Theme.Chart.lineWidth,
                                                 lineCap: .round, lineJoin: .round))
                            }
                        }
                        .chartXAxis {
                            AxisMarks { value in
                                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                                AxisValueLabel {
                                    if let seconds = value.as(Double.self) {
                                        Text("\(Int(seconds / 60))m").font(.caption2)
                                    }
                                }
                            }
                        }
                        .chartYAxis {
                            AxisMarks { value in
                                AxisGridLine().foregroundStyle(Theme.Palette.stroke)
                                AxisValueLabel {
                                    if let deg = value.as(Double.self) {
                                        Text("\(Int(deg))°").font(.caption2)
                                    }
                                }
                            }
                        }
                        .frame(height: Theme.Chart.height(240, typeSize))
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Session temperature timeline")
                      }
                    }
                }
            }
            .padding(Theme.Space.md)
        }
    }

    private func seriesKey(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 4) {
            Capsule().fill(color).frame(width: 14, height: 4)
            Text(label).font(.caption2).foregroundStyle(Theme.Palette.textSecondary)
        }
    }

    private func banner(icon: String, text: String, color: Color) -> some View {
        HStack(spacing: Theme.Space.sm) {
            Image(systemName: icon).font(.title2).foregroundStyle(color)
            Text(text).font(.headline).foregroundStyle(Theme.Palette.textPrimary)
            Spacer()
        }
        .padding(Theme.Space.md)
        .frame(maxWidth: .infinity)
        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }

    private func metric(_ label: String, value: Double?, unit: String, icon: String,
                        detail: String? = nil) -> some View {
        Card {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: icon).font(.title3).foregroundStyle(Theme.Palette.racingRed)
                Text(value.map { "\(fmt($0))\(unit)" } ?? "–")
                    .font(.system(.title2, design: .rounded).weight(.bold))
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text(label).font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                if let detail {
                    Text(detail).font(.caption2).foregroundStyle(Theme.Palette.textTertiary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func fmt(_ v: Double) -> String {
        v.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(v)) : String(format: "%.1f", v)
    }
}

@MainActor
final class WeatherViewModel: ObservableObject {
    @Published var state: Loadable<WeatherResponse> = .idle

    func load(year: Int, round: Int) async {
        if case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.weather(year: year, round: round, session: "R"))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
