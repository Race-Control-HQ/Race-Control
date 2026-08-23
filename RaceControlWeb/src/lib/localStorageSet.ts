"use client";

import { useCallback, useSyncExternalStore } from "react";

const listeners = new Map<string, Set<() => void>>();

function emit(key: string) {
  listeners.get(key)?.forEach((l) => l());
}

function getSnapshot(key: string): string {
  if (typeof window === "undefined") return "[]";
  try {
    return window.localStorage.getItem(key) ?? "[]";
  } catch {
    return "[]";
  }
}

function getServerSnapshot(): string {
  return "[]";
}

function parseSet(raw: string): Set<string> {
  try {
    return new Set(JSON.parse(raw));
  } catch {
    return new Set();
  }
}

/** SSR-safe read/write of a string set persisted to localStorage, shared across components. */
export function useLocalStorageSet(key: string): [Set<string>, (updater: (prev: Set<string>) => Set<string>) => void] {
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
    (updater: (prev: Set<string>) => Set<string>) => {
      const next = updater(parseSet(getSnapshot(key)));
      try {
        window.localStorage.setItem(key, JSON.stringify([...next]));
      } catch {
        // localStorage unavailable (private mode, etc); favorites just won't persist.
      }
      emit(key);
    },
    [key],
  );

  return [parseSet(raw), setValue];
}
