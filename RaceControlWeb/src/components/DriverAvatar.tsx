"use client";

import { useState } from "react";
import { driverInitials } from "@/lib/format";

/**
 * Driver headshot with a graceful fallback to initials.
 *
 * The backend only omits `headshotUrl` when the upstream feed genuinely has
 * no entry for a driver, but the URL it *does* supply is F1's own media CDN,
 * which can 404 or serve a broken image for a driver who only just joined
 * the grid (their photo not yet indexed there). A plain `<img>` with no
 * fallback shows a broken-image icon in that case; this swaps to the same
 * initials placeholder used when there's no URL at all, so a broken source
 * image degrades gracefully instead of looking like an app bug.
 */
export function DriverAvatar({
  src,
  name,
  sizeClassName = "h-10 w-10",
  textClassName = "text-xs",
}: {
  src: string | null | undefined;
  name: string;
  sizeClassName?: string;
  textClassName?: string;
}) {
  const [failed, setFailed] = useState(false);

  if (!src || failed) {
    return (
      <span
        className={`flex ${sizeClassName} shrink-0 items-center justify-center rounded-full bg-surface-raised ${textClassName} font-semibold text-muted`}
      >
        {driverInitials(name)}
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt=""
      className={`${sizeClassName} shrink-0 rounded-full object-cover`}
      onError={() => setFailed(true)}
    />
  );
}
