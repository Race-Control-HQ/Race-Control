import { LoadingState } from "@/components/StateViews";

/**
 * Route-segment loading UI.
 *
 * The circuit page is an async server component that awaits the backend before
 * rendering anything, so without this the browser sits on the *previous* page
 * with no feedback for however long the fetch takes; a cold FastF1 session
 * load can be tens of seconds, which reads as a dead click. Next.js renders
 * this file instantly as the Suspense fallback the moment navigation starts.
 */
export default function Loading() {
  return <LoadingState label="Loading circuit…" />;
}
