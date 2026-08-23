import type { Metadata } from "next";
import { Card, SectionHeading } from "@/components/Card";

export const metadata: Metadata = {
  title: "Open Source & Attribution — RaceControl",
  description: "The data sources and open-source libraries RaceControl is built on, across backend, iOS, Android and web.",
};

type Item = { name: string; href: string; note?: string };

function AttributionTable({ items }: { items: readonly Item[] }) {
  return (
    <div className="overflow-hidden rounded-xl border border-border">
      <table className="w-full text-sm">
        <tbody>
          {items.map((item, i) => (
            <tr key={item.name} className={i !== items.length - 1 ? "border-b border-border" : ""}>
              <td className="w-56 px-4 py-3 align-top font-medium text-foreground">
                <a
                  href={item.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline decoration-border underline-offset-2 hover:decoration-foreground"
                >
                  {item.name}
                </a>
              </td>
              <td className="px-4 py-3 align-top leading-relaxed text-muted">{item.note}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const DATA_SOURCES: Item[] = [
  {
    name: "FastF1",
    href: "https://github.com/theOehrly/Fast-F1",
    note: "The Python library every RaceControl endpoint is built on — pulls timing, telemetry and session data from the F1 live-timing API and caches it to disk. MIT licensed. Docs at docs.fastf1.dev.",
  },
  {
    name: "jolpica-f1",
    href: "https://github.com/jolpica/jolpica-f1",
    note: "A community-run successor to the Ergast API, supplying historical results and standings data. Licensed CC BY-NC-SA 4.0 — used here for non-commercial purposes.",
  },
];

const BACKEND_LIBS: Item[] = [
  { name: "FastAPI", href: "https://github.com/fastapi/fastapi", note: "The web framework serving every REST endpoint." },
  { name: "Uvicorn", href: "https://github.com/encode/uvicorn", note: "ASGI server running the FastAPI app." },
  { name: "pandas / numpy / scipy", href: "https://pandas.pydata.org/", note: "Pulled in transitively by FastF1 for timing and telemetry data." },
  { name: "pyattest", href: "https://github.com/PixelSwap/pyattest", note: "Verifies Apple App Attest attestations server-side." },
  { name: "PyJWT", href: "https://github.com/jpadilla/pyjwt", note: "Signs the short-lived tokens issued after attestation." },
  { name: "cbor2 / cryptography", href: "https://github.com/agronholm/cbor2", note: "CBOR decoding and certificate-chain verification for App Attest." },
  { name: "google-auth", href: "https://github.com/googleapis/google-auth-library-python", note: "Verifies Google Play Integrity tokens server-side." },
];

const WEB_LIBS: Item[] = [
  { name: "Next.js", href: "https://github.com/vercel/next.js", note: "App Router, server components and the BFF proxy route." },
  { name: "React", href: "https://github.com/facebook/react", note: "UI runtime for the web app and this site." },
  { name: "Tailwind CSS", href: "https://github.com/tailwindlabs/tailwindcss", note: "Utility-first styling, shared design tokens with this site." },
  { name: "SWR", href: "https://github.com/vercel/swr", note: "Client-side data fetching and caching for interactive views." },
  { name: "Recharts", href: "https://github.com/recharts/recharts", note: "Lap-time, telemetry and race-trace charts." },
  { name: "Motion", href: "https://github.com/motiondivision/motion", note: "Replay and transition animation." },
  { name: "clsx", href: "https://github.com/lukeed/clsx", note: "Conditional class-name composition." },
];

const ANDROID_LIBS: Item[] = [
  { name: "Kotlin", href: "https://github.com/JetBrains/kotlin", note: "Language for the entire Android app." },
  { name: "Jetpack Compose", href: "https://android.googlesource.com/platform/frameworks/support", note: "Declarative UI toolkit, including Material 3." },
  { name: "Hilt", href: "https://github.com/google/dagger", note: "Dependency injection." },
  { name: "Retrofit / OkHttp", href: "https://github.com/square/retrofit", note: "HTTP client and REST binding to the backend." },
  { name: "kotlinx.serialization", href: "https://github.com/Kotlin/kotlinx.serialization", note: "JSON (de)serialization of API responses." },
  { name: "Coil", href: "https://github.com/coil-kt/coil", note: "Image loading for driver headshots and team logos." },
  { name: "AndroidX DataStore / Security", href: "https://github.com/androidx/androidx", note: "Preferences storage and encrypted token storage." },
  { name: "Google Play Integrity", href: "https://developer.android.com/google/play/integrity", note: "Device attestation for authorised API access." },
];

const IOS_LIBS: Item[] = [
  {
    name: "SwiftUI, Swift Charts, DeviceCheck",
    href: "https://developer.apple.com/documentation/",
    note: "The iOS app has no third-party dependencies — UI, charting and App Attest are all first-party Apple frameworks.",
  },
];

export default function OpenSourcePage() {
  return (
    <div className="flex flex-col gap-14">
      <SectionHeading
        eyebrow="Open source"
        title="Built on public data and open-source software"
        subtitle="RaceControl doesn't run its own F1 timing infrastructure — it stands on the work of the FastF1 and jolpica-f1 communities, plus the open-source libraries listed below. This project is source-available in the same repository as this site."
      />

      <section>
        <h2 className="mb-4 text-lg font-semibold">Data sources</h2>
        <AttributionTable items={DATA_SOURCES} />
      </section>

      <section>
        <h2 className="mb-4 text-lg font-semibold">Backend — Python</h2>
        <AttributionTable items={BACKEND_LIBS} />
      </section>

      <section>
        <h2 className="mb-4 text-lg font-semibold">Web — TypeScript</h2>
        <AttributionTable items={WEB_LIBS} />
      </section>

      <section>
        <h2 className="mb-4 text-lg font-semibold">Android — Kotlin</h2>
        <AttributionTable items={ANDROID_LIBS} />
      </section>

      <section>
        <h2 className="mb-4 text-lg font-semibold">iOS — Swift</h2>
        <AttributionTable items={IOS_LIBS} />
      </section>

      <Card>
        <h3 className="text-base font-semibold">Trademark notice</h3>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          RaceControl is an unofficial project and is not associated in any way with the Formula 1 companies. F1,
          FORMULA ONE, FORMULA 1, FIA FORMULA ONE WORLD CHAMPIONSHIP, GRAND PRIX, and related marks are trademarks
          of Formula One Licensing BV. jolpica-f1 data is used under CC BY-NC-SA 4.0 for non-commercial purposes
          only.
        </p>
      </Card>
    </div>
  );
}
