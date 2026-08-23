import { LoadingState } from "@/components/StateViews";

/** Route-segment loading UI; see circuits/[year]/[round]/loading.tsx. */
export default function Loading() {
  return <LoadingState label="Loading race…" />;
}
