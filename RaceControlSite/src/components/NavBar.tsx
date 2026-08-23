"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import clsx from "clsx";
import { siteConfig } from "@/lib/config";

const TABS = [
  { href: "/", label: "Overview" },
  { href: "/features", label: "Features" },
  { href: "/platforms", label: "Platforms" },
  { href: "/docs", label: "Docs" },
  { href: "/open-source", label: "Open source" },
];

function useIsActive() {
  const pathname = usePathname();
  return (href: string) =>
    href === "/" ? pathname === "/" : pathname === href || pathname?.startsWith(href + "/");
}

export function NavBar() {
  const isActive = useIsActive();

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center gap-6 px-4 py-3">
        <Link href="/" className="flex items-center gap-2 font-bold tracking-tight">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/icon-192.png" alt="" width={28} height={28} className="rounded-md" />
          RaceControl
        </Link>
        <nav className="flex flex-1 items-center gap-1 overflow-x-auto">
          {TABS.map((tab) => (
            <Link
              key={tab.href}
              href={tab.href}
              className={clsx(
                "whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                isActive(tab.href) ? "bg-surface text-foreground" : "text-muted hover:text-foreground",
              )}
            >
              {tab.label}
            </Link>
          ))}
        </nav>
        <a
          href={siteConfig.githubRepo}
          target="_blank"
          rel="noopener noreferrer"
          className="ml-auto shrink-0 rounded-md border border-border px-3 py-1.5 text-sm font-medium text-muted transition-colors hover:border-foreground hover:text-foreground"
        >
          GitHub
        </a>
      </div>
    </header>
  );
}
