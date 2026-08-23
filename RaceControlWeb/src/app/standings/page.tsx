import { Suspense } from "react";
import { callBackend } from "@/lib/server/backend";
import { StandingsClient } from "./StandingsClient";
import { LoadingState } from "@/components/StateViews";

// Depends on live backend data (seasons list) that cannot be resolved at build time.
export const dynamic = "force-dynamic";

export default async function StandingsPage() {
  const seasons = await callBackend<number[]>("/api/seasons");
  return (
    <Suspense fallback={<LoadingState />}>
      <StandingsClient defaultYear={seasons[0]} />
    </Suspense>
  );
}
