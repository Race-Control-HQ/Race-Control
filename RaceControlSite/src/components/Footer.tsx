import Link from "next/link";

const LINK_CLASS =
  "underline decoration-border underline-offset-2 transition-colors hover:text-foreground hover:decoration-foreground";

const NAV = [
  { href: "/features", label: "Features" },
  { href: "/platforms", label: "Platforms" },
  { href: "/docs", label: "Docs" },
  { href: "/docs/api", label: "API reference" },
  { href: "/docs/self-hosting", label: "Self-hosting" },
  { href: "/open-source", label: "Open source" },
];

export function Footer() {
  return (
    <footer className="border-t border-border pb-6">
      <div className="mx-auto max-w-6xl px-4 pt-6">
        <nav className="flex flex-wrap gap-x-5 gap-y-2 text-xs">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href} className="text-muted transition-colors hover:text-foreground">
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
      <div className="mx-auto flex max-w-6xl flex-col gap-1.5 px-4 py-6 text-xs leading-relaxed text-muted">
        <p>
          Race data powered by{" "}
          <a href="https://github.com/theOehrly/Fast-F1" target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
            FastF1
          </a>{" "}
          and the{" "}
          <a href="https://github.com/jolpica/jolpica-f1" target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
            jolpica-f1
          </a>{" "}
          API. jolpica-f1 data is licensed under{" "}
          <a
            href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
            target="_blank"
            rel="noopener noreferrer"
            className={LINK_CLASS}
          >
            CC BY-NC-SA 4.0
          </a>{" "}
          and provided for non-commercial use. Full attribution on the{" "}
          <Link href="/open-source" className={LINK_CLASS}>
            Open Source
          </Link>{" "}
          page.
        </p>
        <p>
          RaceControl and this website are unofficial and are not associated in any way with the Formula 1
          companies. F1, FORMULA ONE, FORMULA 1, FIA FORMULA ONE WORLD CHAMPIONSHIP, GRAND PRIX, and related marks
          are trademarks of Formula One Licensing BV.
        </p>
      </div>
    </footer>
  );
}
