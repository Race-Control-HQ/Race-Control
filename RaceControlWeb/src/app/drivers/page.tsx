import { Suspense } from "react";
import { callBackend } from "@/lib/server/backend";
import { DriversClient } from "./DriversClient";
import { LoadingState } from "@/components/StateViews";

// Depends on live backend data (seasons list) that cannot be resolved at build time.
export const dynamic = "force-dynamic";

export default async function DriversPage() {
  const seasons = await callBackend<number[]>("/api/seasons");
  return (
    <Suspense fallback={<LoadingState />}>
      <DriversClient defaultYear={seasons[0]} />
    </Suspense>
  );
}
