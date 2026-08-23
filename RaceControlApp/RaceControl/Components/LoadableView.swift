import SwiftUI

/// A generic async-loading phase. Every screen that fetches data uses this so
/// loading, error (with retry) and empty states are handled consistently.
enum Loadable<Value> {
    case idle
    case loading
    case loaded(Value)
    case failed(String)
}

/// Renders the appropriate view for a `Loadable` phase, with a built-in
/// retry button on failure, satisfying the "always show loading & error state"
/// mobile-design requirement.
struct LoadableView<Value, Content: View>: View {
    let state: Loadable<Value>
    let retry: () async -> Void
    @ViewBuilder let content: (Value) -> Content

    var body: some View {
        switch state {
        case .idle, .loading:
            LoadingIndicator()
        case .failed(let message):
            ErrorState(message: message) { Task { await retry() } }
        case .loaded(let value):
            content(value)
        }
    }
}

struct LoadingIndicator: View {
    var label: String = "Loading"
    var body: some View {
        VStack(spacing: Theme.Space.md) {
            ProgressView()
                .controlSize(.large)
                .tint(Theme.Palette.racingRed)
            Text(label)
                .font(.footnote)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Palette.background)
    }
}

struct ErrorState: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(spacing: Theme.Space.md) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundStyle(Theme.Palette.warning)
            Text("Something went wrong")
                .font(.headline)
                .foregroundStyle(Theme.Palette.textPrimary)
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.Palette.textSecondary)
                .padding(.horizontal, Theme.Space.lg)
            Button(action: retry) {
                Label("Try Again", systemImage: "arrow.clockwise")
                    .font(.headline)
                    .frame(minHeight: Theme.minTouch)
                    .padding(.horizontal, Theme.Space.lg)
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.Palette.racingRed)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Palette.background)
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let message: String
    var body: some View {
        VStack(spacing: Theme.Space.sm) {
            Image(systemName: icon)
                .font(.largeTitle)
                .foregroundStyle(Theme.Palette.textTertiary)
            Text(title)
                .font(.headline)
                .foregroundStyle(Theme.Palette.textPrimary)
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.Palette.textSecondary)
                .padding(.horizontal, Theme.Space.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Palette.background)
    }
}
