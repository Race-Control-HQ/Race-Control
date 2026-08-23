import type { Metadata } from "next";
import { CodeBlock, DocHeading, DocSection } from "@/components/CodeBlock";
import { Card } from "@/components/Card";

export const metadata: Metadata = {
  title: "Self-hosting — RaceControl docs",
  description: "Deploy the RaceControl backend, web app and site to Coolify from one repository.",
};

const SERVICES = [
  { name: "Backend", dir: "/backend", port: "8000", health: "/api/health", volume: "Yes — /data" },
  { name: "Web app", dir: "/RaceControlWeb", port: "3000", health: "/api/health", volume: "No" },
  { name: "Site", dir: "/RaceControlSite", port: "3000", health: "/api/health", volume: "No" },
];

const ENV = [
  ["APP_ATTEST_ENABLED", "true", "Gate the API with Apple App Attest."],
  ["APPLE_TEAM_ID", "your Team ID", "From developer.apple.com."],
  ["APP_BUNDLE_ID", "com.owlmedia.racecontrol", "Must match the app's bundle id."],
  ["JWT_SECRET", "openssl rand -hex 32", "Signs the short-lived app tokens."],
  ["API_TOKEN", "openssl rand -hex 32", "Shared with the web app's BFF."],
  ["RATE_LIMIT_PER_MINUTE", "120", "Per-IP limit; 0 disables."],
  ["FASTF1_CACHE", "/data/fastf1_cache", "Already the image default."],
  ["WEB_CONCURRENCY", "1", "Must stay 1 — the caches are per-process."],
  ["SESSION_CACHE_MAX", "24", "Lower than the default 48 on a small container."],
  ["ALLOWED_ORIGINS", "unset", "Empty denies all browser origins. Native clients are unaffected."],
];

export default function SelfHostingPage() {
  return (
    <div className="flex flex-col gap-10">
      <DocHeading
        title="Self-hosting"
        intro="The repository is a monorepo where each deployable ships its own Dockerfile, so they deploy as independent Coolify services from the same repo using the Base Directory setting."
      />

      <DocSection title="The three services">
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                {["Service", "Base directory", "Port", "Health check", "Volume"].map((h) => (
                  <th key={h} className="px-4 py-2.5 text-xs font-semibold uppercase tracking-wider text-muted">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {SERVICES.map((service) => (
                <tr key={service.name} className="border-b border-border last:border-0">
                  <td className="px-4 py-2.5 font-medium">{service.name}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-muted">{service.dir}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-muted">{service.port}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-muted">{service.health}</td>
                  <td className="px-4 py-2.5 text-xs text-muted">{service.volume}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-sm leading-relaxed text-muted">
          Create the backend first, then the web app pointed at it. Prefer the backend&apos;s internal Coolify
          service DNS name over its public URL, so traffic stays inside Coolify&apos;s network.
        </p>
        <CodeBlock label="web app environment">{`RACECONTROL_API_BASE_URL=http://backend:8000
RACECONTROL_API_TOKEN=<same value as the backend's API_TOKEN>`}</CodeBlock>
      </DocSection>

      <DocSection title="Backend environment variables">
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <tbody>
              {ENV.map(([name, value, note]) => (
                <tr key={name} className="border-b border-border last:border-0">
                  <td className="px-4 py-2.5 align-top font-mono text-xs text-foreground">{name}</td>
                  <td className="px-4 py-2.5 align-top font-mono text-xs text-muted">{value}</td>
                  <td className="px-4 py-2.5 align-top text-xs leading-relaxed text-muted">{note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-sm leading-relaxed text-muted">
          Set neither <code className="font-mono text-xs text-foreground">APP_ATTEST_ENABLED</code> nor{" "}
          <code className="font-mono text-xs text-foreground">API_TOKEN</code> and the API is open, which is only
          appropriate for local development.
        </p>
      </DocSection>

      <DocSection title="Two things that will bite you">
        <Card>
          <h3 className="text-sm font-semibold">Add a persistent volume at /data</h3>
          <p className="mt-1.5 text-sm leading-relaxed text-muted">
            FastF1 caches every session it downloads there. Without the volume, each redeploy re-downloads
            everything — slow, and it burns through the upstream rate limits. Budget a few GB if you plan to browse
            a lot of telemetry. Loading a session with telemetry is pandas-heavy, so give the container at least
            1 GB of RAM, ideally 2 GB.
          </p>
        </Card>
        <Card>
          <h3 className="text-sm font-semibold">Never expose port 8000 directly</h3>
          <p className="mt-1.5 text-sm leading-relaxed text-muted">
            The per-IP rate limiter trusts the leftmost{" "}
            <code className="font-mono text-xs">X-Forwarded-For</code> entry as the real client address. That is
            only safe because the reverse proxy is the sole ingress path and overwrites that header before it
            reaches the container. If a caller can reach the app without going through the proxy, it can forge the
            header and bypass rate limiting entirely.
          </p>
        </Card>
      </DocSection>

      <DocSection title="Device attestation, for published apps">
        <p className="text-sm leading-relaxed text-muted">
          A shared token can&apos;t ship in a public app — anything bundled in the IPA or APK can be extracted, so
          it isn&apos;t secret. Both published apps instead use their platform&apos;s attestation service: Apple
          App Attest on iOS, Google Play Integrity on Android. The two are fully independent and mint
          interchangeable JWTs, so the backend authorises a request from either without knowing which issued it.
        </p>
        <Card>
          <p className="text-sm leading-relaxed text-muted">
            <span className="font-medium text-foreground">Worth knowing before you ship. </span>
            Neither mechanism&apos;s server-side verification has been tested end-to-end against real hardware in
            this repository — App Attest needs a real iPhone and an Apple developer account, Play Integrity needs a
            real device and a linked Play Console project. Do a real-device smoke test of both first.
          </p>
        </Card>
      </DocSection>
    </div>
  );
}
