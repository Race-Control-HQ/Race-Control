"use client";

import { useCallback, useSyncExternalStore } from "react";

/** Server always reports "no match" so SSR and the first client render agree. */
function getServerSnapshot(): boolean {
  return false;
}

/**
 * SSR-safe `matchMedia` read, via `useSyncExternalStore` for the same reason
 * `useLocalStorageFlag` uses it: reading `window` directly during render
 * would hydrate-mismatch between server and client.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (listener: () => void) => {
      if (typeof window === "undefined" || !window.matchMedia) return () => {};
      const mql = window.matchMedia(query);
      mql.addEventListener("change", listener);
      return () => mql.removeEventListener("change", listener);
    },
    [query],
  );

  const getSnapshot = useCallback(() => {
    if (typeof window === "undefined" || !window.matchMedia) return false;
    return window.matchMedia(query).matches;
  }, [query]);

  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
