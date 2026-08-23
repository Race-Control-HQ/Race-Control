export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
      <span
        role="status"
        aria-label="Loading"
        className="h-7 w-7 animate-spin rounded-full border-2 border-border border-t-racing-red"
      />
      <span className="text-sm text-muted">{label}</span>
      <span className="max-w-xs text-xs text-muted/70">
        First load can take up to a minute while race data is fetched fresh; it&apos;s cached and much faster after that.
      </span>
    </div>
  );
}

export function ErrorState({ message = "Something went wrong." }: { message?: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface px-4 py-6 text-center text-sm text-muted">
      {message}
    </div>
  );
}

export function EmptyState({ message = "No data available." }: { message?: string }) {
  return (
    <div className="rounded-lg border border-dashed border-border px-4 py-10 text-center text-sm text-muted">
      {message}
    </div>
  );
}

export function TeamColorDot({ color }: { color: string | null | undefined }) {
  return (
    <span
      className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
      style={{ backgroundColor: color || "#6b6b72" }}
    />
  );
}

export function StarButton({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        onClick();
      }}
      aria-label={label}
      aria-pressed={active}
      className={
        active
          ? "text-lg text-racing-red transition-colors"
          : "text-lg text-muted transition-colors hover:text-foreground"
      }
    >
      {active ? "★" : "☆"}
    </button>
  );
}
