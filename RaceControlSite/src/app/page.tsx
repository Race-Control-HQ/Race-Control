import Link from "next/link";
import { Card, Pill, SectionHeading } from "@/components/Card";
import { Screenshot } from "@/components/Screenshot";
import { siteConfig } from "@/lib/config";
import { FEATURES } from "@/lib/features";
import { ENDPOINTS } from "@/lib/endpoints";

const SEASONS_COVERED = new Date().getFullYear() - 2018 + 1;

const PIECES = [
  {
    title: "iOS",
    tech: "Swift · SwiftUI",
    desc: "Human Interface Guidelines, dark-first OLED palette, 44pt targets, Dynamic Type, App Attest.",
    href: "/platforms#ios",
  },
  {
    title: "Android",
    tech: "Kotlin · Compose",
    desc: "Material 3, 48dp targets, offline cache, Play Integrity. Divergences from iOS are documented.",
    href: "/platforms#android",
  },
  {
    title: "Web",
    tech: "TypeScript · Next.js",
    desc: "A BFF-backed browser client, so the backend token never reaches the browser.",
    href: "/platforms#web",
  },
  {
    title: "Backend",
    tech: "Python · FastAPI",
    desc: `One service exposing ${ENDPOINTS.length} REST endpoints over FastF1, feeding all three clients.`,
    href: "/docs/architecture",
  },
];

const HIGHLIGHTS = [
  {
    name: "Race replay",
    desc: "Scrub or play a race lap by lap and watch the order animate, with movement arrows, tyre compounds and lap times. 0.5× to 4× speed.",
    href: "/features#replay",
  },
  {
    name: "Race control log",
    desc: "The complete stewards' log — not just flags, but DRS changes, car events, and every investigation, penalty and reprimand.",
    href: "/features#racecontrol",
  },
  {
    name: "Mini-sector dominance",
    desc: "The lap split into ~24 curved segments, each coloured by whichever driver was fastest through it.",
    href: "/features#minisectors",
  },
  {
    name: "Race trace",
    desc: "Cumulative time delta per lap against a fixed median, so vertical distance between two drivers is their real time gap.",
    href: "/features#race-trace",
  },
  {
    name: "Pit-stop ledger",
    desc: "Real pit-lane transit loss rather than stationary time, with entry and rejoin positions and rival-window outcomes.",
    href: "/features#pit-stops",
  },
  {
    name: "Title scenarios",
    desc: "A next-race finish-position matrix between two contenders, with projected margins and clinch conditions.",
    href: "/features#title-scenarios",
  },
];

export default function Home() {
  return (
    <div className="flex flex-col gap-20">
      <section className="grid items-center gap-10 py-4 lg:grid-cols-[1.1fr_0.9fr]">
        <div className="flex flex-col items-start gap-6">
          <Pill>Free · Open source · Unofficial</Pill>
          <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
            Every lap of Formula 1 since 2018,{" "}
            <span className="text-racing-red-text">on every platform.</span>
          </h1>
          <p className="max-w-xl text-base leading-relaxed text-muted">
            RaceControl is a native iOS and Android app plus a web client, all reading one open backend built on{" "}
            <a
              href="https://docs.fastf1.dev"
              target="_blank"
              rel="noopener noreferrer"
              className="text-foreground underline decoration-border underline-offset-2 hover:decoration-foreground"
            >
              FastF1
            </a>
            . Results, standings, telemetry, tyre strategy, race control messages and a lap-by-lap race replay —{" "}
            {SEASONS_COVERED} seasons of it.
          </p>
          <div className="flex flex-wrap gap-3">
            {siteConfig.appStoreUrl ? (
              <a
                href={siteConfig.appStoreUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
              >
                Get it for iOS
              </a>
            ) : (
              <Link
                href="/platforms#ios"
                className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
              >
                Get it for iOS
              </Link>
            )}
            <a
              href={siteConfig.webAppUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-md bg-racing-red px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
            >
              Open on the web
            </a>
            {siteConfig.playStoreUrl ? (
              <a
                href={siteConfig.playStoreUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
              >
                Get it for Android
              </a>
            ) : (
              <Link
                href="/platforms#android"
                className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
              >
                Get it for Android
              </Link>
            )}
          </div>
          <dl className="mt-2 grid grid-cols-3 gap-6">
            {[
              [FEATURES.length, "features"],
              [ENDPOINTS.length, "API endpoints"],
              [SEASONS_COVERED, "seasons"],
            ].map(([value, label]) => (
              <div key={label as string}>
                <dt className="text-2xl font-bold tabular">{value}</dt>
                <dd className="text-xs text-muted">{label}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Screenshot id="iosRaceDetail" priority />
          <Screenshot id="iosSchedule" priority />
        </div>
      </section>

      <section>
        <SectionHeading
          eyebrow="What you can actually do"
          title="Depth well past the classification table"
          subtitle="The results are table stakes. What the apps are really for is everything underneath them."
        />
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {HIGHLIGHTS.map((item) => (
            <Link key={item.name} href={item.href}>
              <Card className="h-full transition-colors hover:border-foreground">
                <h3 className="text-sm font-semibold">{item.name}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-muted">{item.desc}</p>
              </Card>
            </Link>
          ))}
        </div>
        <Link
          href="/features"
          className="mt-5 inline-block text-sm font-medium text-racing-red-text underline decoration-border underline-offset-4 hover:decoration-current"
        >
          See all {FEATURES.length} features, screen by screen →
        </Link>
      </section>

      <section>
        <SectionHeading
          eyebrow="Built for the data"
          title="Charts that mean something"
          subtitle="Track maps rendered from real circuit geometry with numbered corners and DRS zones. Telemetry traced over lap distance. Analytics endpoints arrive pre-binned and chart-ready so three platforms can't compute different answers."
        />
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <Screenshot id="iosTrackMap" />
          <Screenshot id="iosTelemetry" />
          <Screenshot id="iosStandings" />
        </div>
      </section>

      <section>
        <SectionHeading
          eyebrow="One project, four pieces"
          title="Same backend, same data, same features"
          subtitle="Each client follows its own platform's conventions rather than porting another's UI. Where they deliberately differ, it's documented rather than hidden."
        />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {PIECES.map((piece) => (
            <Link key={piece.title} href={piece.href}>
              <Card className="h-full transition-colors hover:border-foreground">
                <h3 className="text-lg font-semibold">{piece.title}</h3>
                <p className="mt-1 text-xs font-medium text-racing-red-text">{piece.tech}</p>
                <p className="mt-3 text-sm leading-relaxed text-muted">{piece.desc}</p>
              </Card>
            </Link>
          ))}
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-2">
        <Card className="flex flex-col items-start gap-3">
          <h3 className="text-lg font-semibold">Run it yourself</h3>
          <p className="text-sm leading-relaxed text-muted">
            The backend, the web app and this site each ship a Dockerfile and deploy as independent services. The
            docs cover local setup, environment variables, volumes and the proxy trust boundary.
          </p>
          <Link
            href="/docs/self-hosting"
            className="mt-auto rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
          >
            Self-hosting guide
          </Link>
        </Card>
        <Card className="flex flex-col items-start gap-3">
          <h3 className="text-lg font-semibold">Built on public data, credited openly</h3>
          <p className="text-sm leading-relaxed text-muted">
            RaceControl runs no timing infrastructure of its own. It stands on FastF1 and jolpica-f1, plus the
            open-source libraries behind all four pieces — every one of them listed and licensed.
          </p>
          <Link
            href="/open-source"
            className="mt-auto rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
          >
            See attribution
          </Link>
        </Card>
      </section>
    </div>
  );
}
