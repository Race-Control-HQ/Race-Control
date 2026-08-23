import { Suspense } from "react";
import { callBackend } from "@/lib/server/backend";
import { HomeDashboard } from "./HomeDashboard";
import { LoadingState } from "@/components/StateViews";

// Depends on live backend data (seasons list) that cannot be resolved at
// build time, so this stays request-time rendered.
export const dynamic = "force-dynamic";

// This page intentionally does NOT read `searchParams`. `defaultYear` below
// is only a fallback for when there's no `?year=` in the URL at all.
// HomeDashboard reads the actual selected year reactively on the client via
// useSearchParams. If this component read `searchParams` (or `year` were
// threaded through it), Next.js would treat every `?year=` change (including
// the season dropdown, a client-side navigation) as needing a full server
// re-render: the dashboard would visibly freeze on the old year while a new
// RSC payload round-trips, then hard-swap to the new one. Staying agnostic to
// `searchParams` here lets Next.js skip that round-trip entirely for
// query-only navigations, so switching seasons stays instant and reactive.
export default async function Home() {
  const seasons = await callBackend<number[]>("/api/seasons");
  const defaultYear = seasons[0];

  return (
    <Suspense fallback={<LoadingState />}>
      <HomeDashboard defaultYear={defaultYear} />
    </Suspense>
  );
}
