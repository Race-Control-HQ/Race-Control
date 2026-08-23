import type { Metadata } from "next";
import { DocHeading, DocSection } from "@/components/CodeBlock";
import { Card } from "@/components/Card";
import { siteConfig } from "@/lib/config";

export const metadata: Metadata = {
  title: "Architecture — RaceControl docs",
  description: "How one FastAPI service feeds three independent native clients, and why the web app needs a BFF.",
};

const LAYERS = [
  {
    title: "iOS · SwiftUI, MVVM",
    body: "Each feature has a @MainActor view model exposing a Loadable state; APIClient is an actor. A permissive JSONValue type absorbs the fact that FastF1 emits numbers where Ergast emits strings for the same field.",
  },
  {
    title: "Android · Compose, MVVM + Hilt",
    body: "Each feature has a ViewModel exposing a UiState StateFlow; networking is Retrofit and OkHttp behind a repository returning Result<T>. An equivalent JsonValue type absorbs the same number-versus-string inconsistency.",
  },
  {
    title: "Web · Next.js App Router",
    body: "Server components fetch the backend directly through a server-only helper. Client components that refetch — season pickers, telemetry scrubbing, replay playback — go through a same-origin proxy route, so the backend token never reaches the browser.",
  },
  {
    title: "Backend · FastAPI over FastF1",
    body: "A thin serialisation layer converts pandas Timedelta, Timestamp and NaN values into JSON-safe output, wrapped by a small cached FastAPI app. A separate analytics service computes the derived, chart-ready endpoints.",
  },
];

export default function ArchitecturePage() {
  return (
    <div className="flex flex-col gap-10">
      <DocHeading
        title="Architecture"
        intro="Four independent clients of one idea. FastF1 is a Python library rather than a hosted service, so the backend runs it on a server and exposes clean JSON over REST; every client consumes that."
      />

      <DocSection title="The shape of it">
        <div className="overflow-x-auto rounded-xl border border-border bg-surface p-5">
          <svg viewBox="0 0 640 260" className="w-full min-w-[520px]" role="img" aria-label="Architecture diagram">
            {[
              { x: 10, y: 20, label: "iOS app", sub: "App Attest" },
              { x: 10, y: 90, label: "Android app", sub: "Play Integrity" },
              { x: 10, y: 160, label: "Browser", sub: "" },
            ].map((box) => (
              <g key={box.label}>
                <rect x={box.x} y={box.y} width="120" height="52" rx="8" fill="#16161a" stroke="#2a2a30" />
                <text x={box.x + 60} y={box.y + (box.sub ? 24 : 30)} textAnchor="middle" fontSize="12" fill="#f2f2f4">
                  {box.label}
                </text>
                {box.sub && (
                  <text x={box.x + 60} y={box.y + 39} textAnchor="middle" fontSize="9" fill="#9a9aa2">
                    {box.sub}
                  </text>
                )}
              </g>
            ))}

            <rect x="190" y="160" width="120" height="52" rx="8" fill="#16161a" stroke="#2a2a30" />
            <text x="250" y="184" textAnchor="middle" fontSize="12" fill="#f2f2f4">
              Web app
            </text>
            <text x="250" y="199" textAnchor="middle" fontSize="9" fill="#9a9aa2">
              BFF · API_TOKEN
            </text>

            <rect x="370" y="90" width="130" height="60" rx="8" fill="#1e1e23" stroke="#e10600" />
            <text x="435" y="114" textAnchor="middle" fontSize="12" fill="#f2f2f4">
              Backend
            </text>
            <text x="435" y="130" textAnchor="middle" fontSize="9" fill="#9a9aa2">
              FastAPI · response cache
            </text>

            <rect x="540" y="20" width="90" height="52" rx="8" fill="#16161a" stroke="#2a2a30" />
            <text x="585" y="43" textAnchor="middle" fontSize="11" fill="#f2f2f4">
              FastF1
            </text>
            <text x="585" y="57" textAnchor="middle" fontSize="9" fill="#9a9aa2">
              disk cache
            </text>

            <rect x="540" y="160" width="90" height="60" rx="8" fill="#16161a" stroke="#2a2a30" />
            <text x="585" y="184" textAnchor="middle" fontSize="10" fill="#f2f2f4">
              F1 timing
            </text>
            <text x="585" y="198" textAnchor="middle" fontSize="10" fill="#f2f2f4">
              Jolpica
            </text>

            {[
              "M 130 46 L 370 110",
              "M 130 116 L 370 118",
              "M 130 186 L 190 186",
              "M 310 186 L 370 140",
              "M 500 112 L 540 60",
              "M 585 72 L 585 160",
            ].map((d) => (
              <path key={d} d={d} stroke="#2a2a30" strokeWidth="1.5" fill="none" />
            ))}
          </svg>
        </div>
        <p className="text-sm leading-relaxed text-muted">
          The mobile apps call the backend directly, each authorised by its platform&apos;s attestation service. A
          browser can&apos;t do that kind of attestation without exposing a secret to every visitor, so the web app
          sits in front as a backend-for-frontend and holds the token server-side.
        </p>
      </DocSection>

      <DocSection title="Per-layer notes">
        <div className="grid gap-3 sm:grid-cols-2">
          {LAYERS.map((layer) => (
            <Card key={layer.title}>
              <h3 className="text-sm font-semibold">{layer.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted">{layer.body}</p>
            </Card>
          ))}
        </div>
      </DocSection>

      <DocSection title="Why the analytics endpoints are chart-ready">
        <p className="text-sm leading-relaxed text-muted">
          Three clients render these with three different chart stacks — Swift Charts, a Compose Canvas layer, and
          Recharts. Any arithmetic left to the client is arithmetic that can diverge between platforms, so the
          derived endpoints arrive pre-binned and pre-sorted, with team colours resolved and axis domains supplied.
          The race trace, for example, uses a baseline fixed for the whole race, which preserves the defining
          property that the vertical distance between two drivers equals their real on-track time gap.
        </p>
        <p className="text-sm leading-relaxed text-muted">
          Keeping three rendering stacks honest against one contract is a standing risk, which is why the repo
          carries a manual cross-platform parity checklist covering loading, empty, error and accessibility states
          for every visualization.
        </p>
        <a
          href={siteConfig.githubRepo + "/blob/main/docs/VISUAL_PARITY.md"}
          target="_blank"
          rel="noopener noreferrer"
          className="self-start rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
        >
          Read the parity checklist ↗
        </a>
      </DocSection>
    </div>
  );
}
