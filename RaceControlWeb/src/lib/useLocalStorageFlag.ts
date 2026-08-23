"use client";

import { useCallback, useSyncExternalStore } from "react";

const listeners = new Map<string, Set<() => void>>();

function emit(key: string) {
  listeners.get(key)?.forEach((l) => l());
}

function getSnapshot(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

/** Server always reports "unset" so SSR and the first client render agree. */
function getServerSnapshot(): string | null {
  return null;
}

/**
 * SSR-safe boolean preference persisted to localStorage.
 *
 * Read through `useSyncExternalStore` rather than `useState` + `useEffect` so
 * the server and first client render produce identical markup; reading
 * localStorage directly during render would hydrate-mismatch.
 */
export function useLocalStorageFlag(key: string, defaultValue = false): [boolean, (next: boolean) => void] {
  const subscribe = useCallback(
    (listener: () => void) => {
      if (!listeners.has(key)) listeners.set(key, new Set());
      const set = listeners.get(key)!;
      set.add(listener);
      return () => set.delete(listener);
    },
    [key],
  );

  const getKeySnapshot = useCallback(() => getSnapshot(key), [key]);
  const raw = useSyncExternalStore(subscribe, getKeySnapshot, getServerSnapshot);

  const setValue = useCallback(
    (next: boolean) => {
      try {
        window.localStorage.setItem(key, next ? "1" : "0");
      } catch {
        // localStorage unavailable (private mode, etc); the preference just
        // won't survive a reload, which is not worth failing the render over.
      }
      emit(key);
    },
    [key],
  );

  return [raw === null ? defaultValue : raw === "1", setValue];
}
