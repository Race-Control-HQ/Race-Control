import { Suspense } from "react";
import { callBackend } from "@/lib/server/backend";
import { CircuitsClient } from "./CircuitsClient";
import { LoadingState } from "@/components/StateViews";

// Depends on live backend data (seasons list) that cannot be resolved at build time.
export const dynamic = "force-dynamic";

export default async function CircuitsPage() {
  const seasons = await callBackend<number[]>("/api/seasons");
  return (
    <Suspense fallback={<LoadingState />}>
      <CircuitsClient defaultYear={seasons[0]} />
    </Suspense>
  );
}
