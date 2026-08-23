"use client";

import { useState } from "react";

/**
 * Team logo with a graceful fallback when the hardcoded F1.com CDN URL is
 * missing or broken (see `_team_logo_url` in the backend; neither FastF1
 * nor Ergast expose team logos, so these are a manually maintained mapping
 * that can drift on a rename or new entrant). The source images are white
 * marks meant for dark backgrounds, so they sit on a small dark chip here
 * to stay legible regardless of the surrounding table's background.
 */
export function TeamLogo({
  src,
  name,
  sizeClassName = "h-6 w-6",
}: {
  src: string | null | undefined;
  name: string | null | undefined;
  sizeClassName?: string;
}) {
  const [failed, setFailed] = useState(false);

  if (!src || failed) return null;

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={name ? `${name} logo` : ""}
      className={`${sizeClassName} shrink-0 rounded bg-black/80 object-contain p-0.5`}
      onError={() => setFailed(true)}
    />
  );
}
