"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo } from "react";

/** Reads/writes the shared `?year=` query param used by every season-scoped list page. */
export function useYearParam(defaultYear: number): [number, (year: number) => void] {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const year = useMemo(() => {
    const raw = searchParams.get("year");
    const parsed = raw ? parseInt(raw, 10) : NaN;
    return Number.isFinite(parsed) ? parsed : defaultYear;
  }, [searchParams, defaultYear]);

  const setYear = useCallback(
    (next: number) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("year", String(next));
      router.push(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [router, pathname, searchParams],
  );

  return [year, setYear];
}
