import SwiftUI

struct TitleScenarioMatrixView: View {
    let year: Int
    let throughRound: Int?
    @State private var data: TitleScenariosResponse?

    var body: some View {
        Group {
            if let data, data.available, data.drivers.count >= 2 {
                VStack(alignment: .leading, spacing: Theme.Space.sm) {
                    Text("TITLE PERMUTATIONS")
                        .font(.caption.weight(.bold)).tracking(1)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Text("CHAMPIONSHIP AFTER THE NEXT RACE")
                        .font(.subheadline.weight(.semibold))
                    Text("\(data.drivers[0].code) \(points(data.drivers[0].points)) pts · \(data.drivers[1].code) \(points(data.drivers[1].points)) pts · \(data.roundsRemaining) race\(data.roundsRemaining == 1 ? "" : "s") remaining")
                        .font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                    Text("Rows are \(data.drivers[0].code)'s finish; columns are \(data.drivers[1].code)'s. Each tile is the projected margin for \(data.drivers[0].code): + ahead, − behind.")
                        .font(.caption).foregroundStyle(Theme.Palette.textSecondary)
                    if let summary = data.summary {
                        Text("\(summary) The tile numbers show how the margin changes.")
                            .font(.subheadline)
                            .padding(Theme.Space.sm)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.Palette.surfaceElevated,
                                        in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                    }
                    if let text = data.clinchText {
                        Text(text).font(.subheadline.weight(.semibold))
                            .padding(Theme.Space.sm)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.Palette.positive.opacity(0.12),
                                        in: RoundedRectangle(cornerRadius: Theme.Radius.sm))
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 3) {
                                Text("\(data.drivers[0].code) ↓\n\(data.drivers[1].code) →")
                                    .font(.system(size: 8, weight: .semibold))
                                    .multilineTextAlignment(.center)
                                    .frame(width: 46, height: 28)
                                ForEach(data.positions, id: \.self) { Text(label($0)).font(.caption2).frame(width: 34) }
                            }
                            let maxMargin = max(1, data.cells.map { abs($0.margin) }.max() ?? 1)
                            ForEach(data.positions, id: \.self) { row in
                                HStack(spacing: 3) {
                                    Text(label(row)).font(.caption2).frame(width: 46)
                                    ForEach(data.positions, id: \.self) { column in
                                        let value = cell(data, row, column)
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 3)
                                                .fill(color(value, maxMargin: maxMargin))
                                            Text(value.map { margin($0.margin) } ?? "—")
                                                .font(.system(size: 9, weight: .semibold, design: .rounded))
                                                .foregroundStyle(.white)
                                                .minimumScaleFactor(0.7)
                                        }
                                        .frame(width: 34, height: 34)
                                        .accessibilityLabel(accessibilityLabel(value, data: data))
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(Theme.Space.md)
                .background(Theme.Palette.surface,
                            in: RoundedRectangle(cornerRadius: Theme.Radius.md))
            }
        }
        .task(id: "\(year)-\(throughRound.map(String.init) ?? "live")") {
            data = try? await APIClient.shared.titleScenarios(year: year, throughRound: throughRound)
        }
    }

    private func cell(_ data: TitleScenariosResponse, _ row: Int, _ column: Int) -> TitleScenarioCell? {
        data.cells.first { $0.d1Position == row && $0.d2Position == column }
    }
    private func label(_ position: Int) -> String { position == 0 ? "DNF" : "P\(position)" }
    private func points(_ value: Double) -> String {
        value.rounded() == value ? String(Int(value)) : String(format: "%.1f", value)
    }
    private func margin(_ value: Double) -> String {
        if value == 0 { return "TIE" }
        return value > 0 ? "+\(points(value))" : points(value)
    }
    private func color(_ cell: TitleScenarioCell?, maxMargin: Double) -> Color {
        guard let cell else { return Theme.Palette.textTertiary.opacity(0.7) }
        let strength = 0.2 + 0.42 * min(1, abs(cell.margin) / maxMargin)
        return switch cell.outcome {
        case "D1_CLINCHED": Theme.Palette.positive.opacity(0.92)
        case "D2_CLINCHED": Theme.Palette.negative.opacity(0.92)
        case "D1_LEADS": Theme.Palette.positive.opacity(strength)
        case "D2_LEADS": Theme.Palette.negative.opacity(strength)
        default: Theme.Palette.textTertiary.opacity(0.7)
        }
    }
    private func accessibilityLabel(_ cell: TitleScenarioCell?, data: TitleScenariosResponse) -> String {
        guard let cell else { return "No scenario data" }
        return "\(data.drivers[0].code) \(label(cell.d1Position)), \(data.drivers[1].code) \(label(cell.d2Position)), projected margin \(margin(cell.margin))"
    }
}
