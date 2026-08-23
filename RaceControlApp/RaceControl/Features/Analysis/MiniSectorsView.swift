import SwiftUI

struct MiniSectorsView: View {
    let year: Int
    let round: Int
    let title: String
    @StateObject private var vm = MiniSectorsViewModel()

    var body: some View {
        LoadableView(state: vm.state) {
            await vm.load(year: year, round: round, force: true)
        } content: { data in
            if !data.available || data.segments.isEmpty {
                EmptyStateView(icon: "map", title: "No Mini-Sectors",
                               message: "No qualifying telemetry is available.")
            } else {
                ScrollView {
                    VStack(spacing: Theme.Space.md) {
                        MiniSectorCanvas(segments: data.segments)
                            .frame(height: 430)
                            .padding(Theme.Space.md)
                            .background(.black, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 90))],
                                  spacing: Theme.Space.sm) {
                            ForEach(data.legend) { item in
                                HStack(spacing: 5) {
                                    Circle().fill(Color.team(item.teamColor)).frame(width: 9, height: 9)
                                    Text("\(item.code) · \(item.segmentsWon)")
                                }
                                .font(.caption.bold())
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(Theme.Palette.surfaceElevated, in: Capsule())
                            }
                        }
                    }
                    .padding(Theme.Space.md)
                }
            }
        }
        .navigationTitle("Mini-Sectors")
        .navigationBarTitleDisplayMode(.inline)
        .background(Theme.Palette.background)
        .task { await vm.load(year: year, round: round) }
    }
}

private struct MiniSectorCanvas: View {
    let segments: [MiniSectorSegment]

    var body: some View {
        Canvas { context, size in
            let points = segments.flatMap(\.points).filter { $0.count >= 2 }
            guard let minX = points.map({ $0[0] }).min(),
                  let maxX = points.map({ $0[0] }).max(),
                  let minY = points.map({ $0[1] }).min(),
                  let maxY = points.map({ $0[1] }).max() else { return }
            let spanX = max(maxX - minX, 1)
            let spanY = max(maxY - minY, 1)
            let scale = min((size.width - 20) / spanX, (size.height - 20) / spanY)
            let offsetX = (size.width - spanX * scale) / 2
            let offsetY = (size.height - spanY * scale) / 2
            for segment in segments {
                var path = Path()
                for (index, point) in segment.points.enumerated() where point.count >= 2 {
                    let mapped = CGPoint(
                        x: offsetX + (point[0] - minX) * scale,
                        y: size.height - offsetY - (point[1] - minY) * scale
                    )
                    if index == 0 { path.move(to: mapped) } else { path.addLine(to: mapped) }
                }
                context.stroke(path, with: .color(Color.team(segment.teamColor)),
                               style: .init(lineWidth: 6, lineCap: .round, lineJoin: .round))
            }
        }
    }
}

@MainActor
final class MiniSectorsViewModel: ObservableObject {
    @Published var state: Loadable<MiniSectorsResponse> = .idle

    func load(year: Int, round: Int, force: Bool = false) async {
        if !force, case .loaded = state { return }
        state = .loading
        do {
            state = .loaded(try await APIClient.shared.miniSectors(year: year, round: round))
        } catch {
            state = .failed((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
    }
}
