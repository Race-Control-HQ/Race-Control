"use client";

import { useMemo, useState } from "react";
import clsx from "clsx";
import { ENDPOINTS, ENDPOINT_GROUPS, type EndpointGroup } from "@/lib/endpoints";
import { siteConfig } from "@/lib/config";

export function ApiReference() {
  const [query, setQuery] = useState("");
  const [group, setGroup] = useState<EndpointGroup | "all">("all");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ENDPOINTS.filter((endpoint) => {
      if (group !== "all" && endpoint.group !== group) return false;
      if (!q) return true;
      return (
        endpoint.path.toLowerCase().includes(q) ||
        endpoint.summary.toLowerCase().includes(q) ||
        (endpoint.params ?? []).some((p) => p.name.toLowerCase().includes(q))
      );
    });
  }, [query, group]);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search endpoints, e.g. telemetry or standings"
          aria-label="Search endpoints"
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted focus:border-foreground focus:outline-none"
        />
        <div className="flex flex-wrap gap-1.5">
          <FilterChip active={group === "all"} onClick={() => setGroup("all")}>
            All
          </FilterChip>
          {ENDPOINT_GROUPS.map((g) => (
            <FilterChip key={g} active={group === g} onClick={() => setGroup(g)}>
              {g}
            </FilterChip>
          ))}
        </div>
      </div>

      <p className="text-xs text-muted">
        {filtered.length} of {ENDPOINTS.length} endpoints. Base URL{" "}
        <code className="rounded border border-border bg-surface-raised px-1.5 py-0.5 font-mono text-[11px]">
          {siteConfig.backendApiUrl}
        </code>
      </p>

      <div className="flex flex-col gap-2">
        {filtered.map((endpoint) => (
          <div key={endpoint.path + endpoint.summary} className="rounded-xl border border-border bg-surface p-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded bg-surface-raised px-1.5 py-0.5 font-mono text-[10px] font-semibold text-positive">
                {endpoint.method}
              </span>
              <code className="break-all font-mono text-xs text-foreground">{endpoint.path}</code>
              {endpoint.expensive && (
                <span
                  title="The first uncached request loads telemetry and is intentionally the expensive path."
                  className="rounded-full border border-border px-2 py-0.5 text-[10px] text-muted"
                >
                  slow when cold
                </span>
              )}
            </div>
            <p className="mt-2 text-sm leading-relaxed text-muted">{endpoint.summary}</p>
            {endpoint.params && (
              <dl className="mt-2.5 flex flex-col gap-1 border-l-2 border-border pl-3">
                {endpoint.params.map((param) => (
                  <div key={param.name} className="flex flex-wrap gap-x-2 text-xs">
                    <dt className="font-mono font-medium text-foreground">?{param.name}</dt>
                    <dd className="flex-1 text-muted">{param.description}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
        ))}
      </div>

      {filtered.length === 0 && (
        <div className="rounded-xl border border-border bg-surface p-8 text-center">
          <p className="text-sm font-medium">No endpoints match that search</p>
          <p className="mt-1 text-sm text-muted">Try a broader term, or clear the group filter.</p>
        </div>
      )}
    </div>
  );
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={clsx(
        "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "border-foreground bg-surface-raised text-foreground"
          : "border-border text-muted hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}
