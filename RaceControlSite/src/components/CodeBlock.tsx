export function CodeBlock({ children, label }: { children: string; label?: string }) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface">
      {label && (
        <div className="border-b border-border px-3 py-1.5 text-[10px] uppercase tracking-wider text-muted">
          {label}
        </div>
      )}
      <pre className="overflow-x-auto p-3">
        <code className="font-mono text-xs leading-relaxed text-foreground">{children}</code>
      </pre>
    </div>
  );
}

export function DocHeading({ title, intro }: { title: string; intro?: string }) {
  return (
    <div>
      <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
      {intro && <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted">{intro}</p>}
    </div>
  );
}

export function DocSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">{title}</h2>
      {children}
    </section>
  );
}
