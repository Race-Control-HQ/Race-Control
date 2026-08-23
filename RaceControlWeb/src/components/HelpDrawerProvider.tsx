"use client";

import { createContext, useContext, useMemo, useState, useCallback } from "react";

interface HelpDrawerContextValue {
  isOpen: boolean;
  toggle: () => void;
  open: () => void;
  close: () => void;
}

const HelpDrawerContext = createContext<HelpDrawerContextValue | null>(null);

export function HelpDrawerProvider({ children }: { children: React.ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);

  const toggle = useCallback(() => setIsOpen((v) => !v), []);
  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);

  const value = useMemo<HelpDrawerContextValue>(() => ({ isOpen, toggle, open, close }), [isOpen, toggle, open, close]);

  return <HelpDrawerContext.Provider value={value}>{children}</HelpDrawerContext.Provider>;
}

export function useHelpDrawer(): HelpDrawerContextValue {
  const ctx = useContext(HelpDrawerContext);
  if (!ctx) throw new Error("useHelpDrawer must be used within HelpDrawerProvider");
  return ctx;
}
