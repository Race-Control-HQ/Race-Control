import type { Metadata } from "next";
import Link from "next/link";
import { Card, Pill, SectionHeading } from "@/components/Card";
import { Screenshot } from "@/components/Screenshot";
import { siteConfig } from "@/lib/config";
import { FEATURES, PLATFORM_LABELS, type Platform } from "@/lib/features";

export const metadata: Metadata = {
  title: "Platforms — RaceControl",
  description:
    "RaceControl on iOS, Android and the web: requirements, design conventions, deliberate platform divergences, and where to get each.",
};

function StoreLink({ href, fallback, label }: { href: string | null; fallback: string; label: string }) {
  if (href) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className="rounded-md bg-racing-red px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
      >
        {label}
      </a>
    );
  }
  return (
    <span
      title="Not yet published — build from source in the meantime"
      className="cursor-default rounded-md border border-dashed border-border px-4 py-2 text-sm font-semibold text-muted"
    >
      {fallback}
    </span>
  );
}

function countFor(platform: Platform) {
  return FEATURES.filter((f) => f.platforms.includes(platform)).length;
}

export default function PlatformsPage() {
  return (
    <div className="flex flex-col gap-16">
      <SectionHeading
        eyebrow="Platforms"
        title="Three idiomatic clients, not one app wrapped three times"
        subtitle="Every platform gets a build against its own conventions, reading the same backend. Where behaviour deliberately differs, it's called out here and documented in full in the repository."
      />

      {/* iOS */}
      <section id="ios" className="scroll-mt-24">
        <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
          <Card className="flex flex-col gap-5">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-xl font-bold">iOS</h2>
              <Pill>Swift · SwiftUI</Pill>
              <Pill>iOS 17+</Pill>
              <Pill>{countFor("ios")} features</Pill>
            </div>
            <p className="text-sm leading-relaxed text-muted">
              Built against Apple&apos;s Human Interface Guidelines: a dark-first OLED-tuned palette, official F1
              team and tyre colours, 44pt+ touch targets, Dynamic Type, semantic SF Symbols and tab-bar navigation
              capped at five tabs. Every screen has consistent loading, error-with-retry and empty states.
            </p>
            <ul className="flex flex-col gap-1.5 text-sm">
              {[
                "MVVM: each feature has a @MainActor view model exposing a Loadable state",
                "Charts render with Swift Charts; track maps are custom Core Graphics",
                "Apple App Attest authorises every request — no key ships in the bundle",
                "No third-party dependencies at all",
              ].map((item) => (
                <li key={item} className="flex items-start gap-2 leading-relaxed">
                  <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-racing-red" />
                  {item}
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-3">
              <StoreLink
                href={siteConfig.appStoreUrl}
                fallback="Coming to the App Store"
                label="Download on the App Store"
              />
              <a
                href={siteConfig.iosGithubUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
              >
                View source
              </a>
            </div>
          </Card>
          <Screenshot id="iosRaceClassification" priority />
        </div>
      </section>

      {/* Android */}
      <section id="android" className="scroll-mt-24">
        <Card className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-3">
            <h2 className="text-xl font-bold">Android</h2>
            <Pill>Kotlin · Jetpack Compose</Pill>
            <Pill>Android 8+</Pill>
            <Pill>{countFor("android")} features</Pill>
          </div>
          <p className="text-sm leading-relaxed text-muted">
            Built against Material 3 and Android accessibility conventions. Same palette and colour semantics as
            iOS, but every divergence below is deliberate rather than incidental.
          </p>
          <p className="rounded-lg border border-border bg-surface-raised px-4 py-3 text-xs leading-relaxed text-muted">
            Android product capture pending. The mobile screenshots on this site are labelled iOS captures so the
            platform-specific interface is never presented as Android.
          </p>
          <ul className="flex flex-col gap-1.5 text-sm">
            {[
              "48dp touch targets — Android's accessibility minimum, above iOS's 44pt",
              "Material You dynamic colour deliberately disabled: it would collide with the team and tyre colours the app uses to convey meaning",
              "Tab rows where option counts vary (sessions range from 1 to 6 a weekend)",
              "Settings moved to the overflow menu — top-left is reserved for navigation",
              "A 10 MB offline cache backs schedule and standings, with an explicit banner",
              "Charts render through an in-house Compose Canvas layer; Play Integrity authorises requests",
            ].map((item) => (
              <li key={item} className="flex items-start gap-2 leading-relaxed">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-racing-red" />
                {item}
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap gap-3">
            <StoreLink
              href={siteConfig.playStoreUrl}
              fallback="Coming to Google Play"
              label="Get it on Google Play"
            />
            <a
              href={siteConfig.androidGithubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
            >
              View source
            </a>
            <a
              href={siteConfig.githubRepo + "/blob/main/RaceControlAndroid/docs/FEATURES.md"}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
            >
              Every divergence, documented ↗
            </a>
          </div>
        </Card>
      </section>

      {/* Web */}
      <section id="web" className="scroll-mt-24">
        <div className="grid gap-6 lg:grid-cols-[1fr_440px]">
          <Card className="flex flex-col gap-5">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-xl font-bold">Web</h2>
              <Pill>TypeScript · Next.js</Pill>
              <Pill>Any browser</Pill>
              <Pill>{countFor("web")} features</Pill>
            </div>
            <p className="text-sm leading-relaxed text-muted">
              A browser can&apos;t do the device attestation the mobile apps use without exposing a secret to every
              visitor, so the web app fronts the backend as a backend-for-frontend. Server components fetch
              directly; interactive client components go through a same-origin proxy route, so the backend token
              never reaches the browser.
            </p>
            <ul className="flex flex-col gap-1.5 text-sm">
              {[
                "Next.js App Router with server components for initial loads",
                "A GET-only proxy exposes just the data surface, never the attestation endpoints",
                "Charts render with Recharts; favourites are client-only, matching mobile",
                "Session reminder notifications are the one deliberate omission — there's no clean stateless-web equivalent",
              ].map((item) => (
                <li key={item} className="flex items-start gap-2 leading-relaxed">
                  <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-racing-red" />
                  {item}
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-3">
              <a
                href={siteConfig.webAppUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-md bg-racing-red px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
              >
                Open the web app
              </a>
              <a
                href={siteConfig.webAppGithubUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
              >
                View source
              </a>
            </div>
          </Card>
          <Screenshot id="webResults" />
        </div>
      </section>

      <section>
        <SectionHeading eyebrow="More screens" title="The same data, three ways" />
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <Screenshot id="webTrackMap" />
          <Screenshot id="iosConstructors" />
          <Screenshot id="iosDrivers" />
        </div>
      </section>

      <section>
        <SectionHeading
          eyebrow="Availability"
          title="What's on which platform"
          subtitle="Most features are on all three. These are the ones that aren't, and why."
        />
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="px-4 py-2.5 text-xs font-semibold uppercase tracking-wider text-muted">Feature</th>
                {(["ios", "android", "web"] as Platform[]).map((p) => (
                  <th
                    key={p}
                    className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wider text-muted"
                  >
                    {PLATFORM_LABELS[p]}
                  </th>
                ))}
                <th className="px-4 py-2.5 text-xs font-semibold uppercase tracking-wider text-muted">Why</th>
              </tr>
            </thead>
            <tbody>
              {FEATURES.filter((f) => f.platforms.length < 3).map((feature) => (
                <tr key={feature.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-2.5 font-medium">
                    <Link href={`/features#${feature.id}`} className="hover:text-racing-red-text">
                      {feature.name}
                    </Link>
                  </td>
                  {(["ios", "android", "web"] as Platform[]).map((p) => (
                    <td key={p} className="px-4 py-2.5 text-center">
                      {feature.platforms.includes(p) ? (
                        <span className="text-positive">✓</span>
                      ) : (
                        <span className="text-muted/40">—</span>
                      )}
                    </td>
                  ))}
                  <td className="px-4 py-2.5 text-xs leading-relaxed text-muted">{feature.divergence}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
