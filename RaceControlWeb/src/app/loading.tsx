import { LoadingState } from "@/components/StateViews";

// Shown while `page.tsx` is resolving server-side; most of the time that's
// near-instant, but on a cold cache it also warms the backend's circuit/
// weather data for the dashboard's headline race, which can take up to a
// minute the first time. Without this, that wait would just be a blank tab.
export default function RootLoading() {
  return <LoadingState label="Loading RaceControl…" />;
}
