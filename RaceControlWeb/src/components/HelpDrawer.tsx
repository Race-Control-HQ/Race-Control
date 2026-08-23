"use client";

import { useEffect } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import clsx from "clsx";
import { useHelpDrawer } from "@/components/HelpDrawerProvider";
import { resolveHelpContent } from "@/lib/helpContent";

export function HelpDrawer() {
  const { isOpen, close } = useHelpDrawer();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  useEffect(() => {
    if (!isOpen) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") close();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [isOpen, close]);

  const content = resolveHelpContent(pathname ?? "/", searchParams ?? new URLSearchParams());

  return (
    <>
      <div
        aria-hidden={!isOpen}
        onClick={close}
        className={clsx(
          "fixed inset-0 z-40 bg-black/60 transition-opacity duration-200",
          isOpen ? "opacity-100" : "pointer-events-none opacity-0",
        )}
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Page help"
        aria-hidden={!isOpen}
        inert={!isOpen ? true : undefined}
        className={clsx(
          "fixed inset-y-0 right-0 z-50 flex w-full max-w-full flex-col border-l border-border bg-surface shadow-xl transition-transform duration-200 ease-out sm:w-[360px]",
          isOpen ? "translate-x-0" : "translate-x-full",
        )}
      >
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{content.title}</h2>
          <button
            type="button"
            onClick={close}
            aria-label="Close help panel"
            className="rounded-md p-1 text-muted transition-colors hover:bg-surface-raised hover:text-foreground"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-4 py-4">
          <div className="flex flex-col gap-3 text-sm leading-relaxed text-foreground/90">
            {content.body.map((paragraph, i) => (
              <p key={i}>{paragraph}</p>
            ))}
          </div>
        </div>
      </aside>
    </>
  );
}
