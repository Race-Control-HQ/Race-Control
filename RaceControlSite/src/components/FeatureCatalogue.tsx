"use client";

import { useMemo, useState } from "react";
import clsx from "clsx";
import { FEATURES, FEATURE_CATEGORIES, PLATFORM_LABELS, type Platform } from "@/lib/features";

const PLATFORM_FILTERS: (Platform | "all")[] = ["all", "ios", "android", "web"];

export function FeatureCatalogue() {
  const [platform, setPlatform] = useState<Platform | "all">("all");
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return FEATURES.filter((feature) => {
      const platformOk = platform === "all" || feature.platforms.includes(platform);
      if (!platformOk) return false;
      if (!q) return true;
      return (
        feature.name.toLowerCase().includes(q) ||
        feature.tagline.toLowerCase().includes(q) ||
        feature.detail.toLowerCase().includes(q) ||
        feature.highlights.some((h) => h.toLowerCase().includes(q))
      );
    });
  }, [platform, query]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="flex gap-1 rounded-lg border border-border bg-surface p-1">
          {PLATFORM_FILTERS.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setPlatform(option)}
              className={clsx(
                "rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
                platform === option ? "bg-surface-raised text-foreground" : "text-muted hover:text-foreground",
              )}
            >
              {option === "all" ? "All platforms" : PLATFORM_LABELS[option]}
            </button>
          ))}
        </div>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search features"
          aria-label="Search features"
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted focus:border-foreground focus:outline-none sm:max-w-xs"
        />
      </div>

      <p className="text-xs text-muted">
        Showing {filtered.length} of {FEATURES.length} features
      </p>

      {FEATURE_CATEGORIES.map((category) => {
        const items = filtered.filter((f) => f.category === category);
        if (items.length === 0) return null;

        return (
          <section key={category} className="flex flex-col gap-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-racing-red-text">{category}</h2>
            <div className="grid gap-3">
              {items.map((feature) => (
                <article
                  key={feature.id}
                  id={feature.id}
                  className="scroll-mt-24 rounded-xl border border-border bg-surface p-5"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold">{feature.name}</h3>
                      <p className="mt-0.5 text-sm text-racing-red-text">{feature.tagline}</p>
                    </div>
                    <div className="flex gap-1">
                      {(["ios", "android", "web"] as Platform[]).map((p) => (
                        <span
                          key={p}
                          title={
                            feature.platforms.includes(p)
                              ? `Available on ${PLATFORM_LABELS[p]}`
                              : `Not on ${PLATFORM_LABELS[p]}`
                          }
                          className={clsx(
                            "rounded px-1.5 py-0.5 text-[10px] font-medium",
                            feature.platforms.includes(p)
                              ? "bg-surface-raised text-foreground"
                              : "text-muted/40 line-through",
                          )}
                        >
                          {PLATFORM_LABELS[p]}
                        </span>
                      ))}
                    </div>
                  </div>

                  <p className="mt-3 text-sm leading-relaxed text-muted">{feature.detail}</p>

                  <ul className="mt-3 flex flex-col gap-1.5">
                    {feature.highlights.map((highlight) => (
                      <li key={highlight} className="flex items-start gap-2 text-sm leading-relaxed">
                        <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-racing-red" />
                        {highlight}
                      </li>
                    ))}
                  </ul>

                  {feature.divergence && (
                    <p className="mt-3 border-l-2 border-border pl-3 text-xs leading-relaxed text-muted">
                      <span className="font-medium text-foreground">Platform note. </span>
                      {feature.divergence}
                    </p>
                  )}

                  {feature.endpoints.length > 0 && (
                    <div className="mt-3 flex flex-wrap items-center gap-1.5">
                      <span className="text-[10px] uppercase tracking-wider text-muted">Data</span>
                      {feature.endpoints.map((endpoint) => (
                        <code
                          key={endpoint}
                          className="rounded border border-border bg-surface-raised px-1.5 py-0.5 font-mono text-[10px] text-muted"
                        >
                          {endpoint}
                        </code>
                      ))}
                    </div>
                  )}
                </article>
              ))}
            </div>
          </section>
        );
      })}

      {filtered.length === 0 && (
        <div className="rounded-xl border border-border bg-surface p-8 text-center">
          <p className="text-sm font-medium">No features match that search</p>
          <p className="mt-1 text-sm text-muted">Try a different term, or clear the platform filter.</p>
        </div>
      )}
    </div>
  );
}
