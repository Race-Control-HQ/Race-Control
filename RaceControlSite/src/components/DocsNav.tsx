"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import clsx from "clsx";
import { siteConfig } from "@/lib/config";

const SECTIONS = [
  {
    title: "Getting started",
    links: [
      { href: "/docs", label: "Overview" },
      { href: "/docs/getting-started", label: "Run it locally" },
    ],
  },
  {
    title: "Reference",
    links: [
      { href: "/docs/api", label: "API reference" },
      { href: "/docs/architecture", label: "Architecture" },
    ],
  },
  {
    title: "Operations",
    links: [{ href: "/docs/self-hosting", label: "Self-hosting" }],
  },
];

const EXTERNAL = [
  { href: siteConfig.githubRepo + "/blob/main/README.md", label: "Full README" },
  {
    href: siteConfig.githubRepo + "/blob/main/RaceControlAndroid/docs/FEATURES.md",
    label: "Feature inventory",
  },
  { href: siteConfig.githubRepo + "/blob/main/docs/VISUAL_PARITY.md", label: "Parity checklist" },
  { href: siteConfig.backendDocsUrl, label: "Swagger / OpenAPI" },
];

export function DocsNav() {
  const pathname = usePathname();

  return (
    <nav className="flex flex-col gap-5 text-sm">
      {SECTIONS.map((section) => (
        <div key={section.title} className="flex flex-col gap-1">
          <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted">{section.title}</p>
          {section.links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={clsx(
                "rounded-md px-2 py-1 transition-colors",
                pathname === link.href
                  ? "bg-surface text-foreground"
                  : "text-muted hover:text-foreground",
              )}
            >
              {link.label}
            </Link>
          ))}
        </div>
      ))}

      <div className="flex flex-col gap-1 border-t border-border pt-4">
        <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted">In the repo</p>
        {EXTERNAL.map((link) => (
          <a
            key={link.href}
            href={link.href}
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-md px-2 py-1 text-muted transition-colors hover:text-foreground"
          >
            {link.label} ↗
          </a>
        ))}
      </div>
    </nav>
  );
}
