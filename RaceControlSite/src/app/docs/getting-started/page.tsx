import type { Metadata } from "next";
import { CodeBlock, DocHeading, DocSection } from "@/components/CodeBlock";
import { Card } from "@/components/Card";

export const metadata: Metadata = {
  title: "Run it locally — RaceControl docs",
  description: "Get the RaceControl backend and any of the iOS, Android or web clients running locally.",
};

export default function GettingStartedPage() {
  return (
    <div className="flex flex-col gap-10">
      <DocHeading
        title="Run it locally"
        intro="Start the backend first — all three clients are just readers of it. Everything below assumes you've cloned the repository."
      />

      <DocSection title="1. The backend">
        <p className="text-sm leading-relaxed text-muted">
          Requires Python 3.10 or newer. The script creates a virtualenv, installs dependencies and starts the API.
        </p>
        <CodeBlock label="bash">{`cd backend
./run.sh`}</CodeBlock>
        <p className="text-sm leading-relaxed text-muted">
          The API starts on <code className="font-mono text-xs text-foreground">http://localhost:8000</code>, with
          interactive Swagger UI at <code className="font-mono text-xs text-foreground">/docs</code>. With no auth
          environment variables set, it runs open — which is the intended local-development path.
        </p>
        <Card>
          <p className="text-sm leading-relaxed text-muted">
            <span className="font-medium text-foreground">First request for a race is slow. </span>
            FastF1 downloads and caches that session to{" "}
            <code className="font-mono text-xs">backend/.fastf1_cache/</code>. It&apos;s slow once, then fast
            forever after.
          </p>
        </Card>
      </DocSection>

      <DocSection title="2a. The iOS app">
        <p className="text-sm leading-relaxed text-muted">Requires Xcode 16 or newer, on macOS.</p>
        <CodeBlock label="steps">{`open RaceControlApp/RaceControl.xcodeproj
# select an iPhone simulator, then press Cmd-R`}</CodeBlock>
        <p className="text-sm leading-relaxed text-muted">
          The app points at the production backend by default. To use your local one, change{" "}
          <code className="font-mono text-xs text-foreground">AppConfig.apiBaseURL</code> in{" "}
          <code className="font-mono text-xs text-foreground">RaceControl/Networking/APIClient.swift</code>. On a
          physical iPhone use your Mac&apos;s LAN address, and keep both on the same Wi-Fi.
        </p>
      </DocSection>

      <DocSection title="2b. The Android app">
        <p className="text-sm leading-relaxed text-muted">
          Requires Android Studio Ladybug or newer and JDK 17. Open{" "}
          <code className="font-mono text-xs text-foreground">RaceControlAndroid/</code> as a project, or build from
          the command line.
        </p>
        <CodeBlock label="bash">{`cd RaceControlAndroid
./gradlew assembleDebug`}</CodeBlock>
        <p className="text-sm leading-relaxed text-muted">
          The app defaults to <code className="font-mono text-xs text-foreground">http://10.0.2.2:8000</code>, the
          emulator&apos;s alias for localhost on your machine, so an emulator needs no setup. On a physical device,
          set your machine&apos;s LAN address under the overflow menu, then Settings, then Server address.
        </p>
      </DocSection>

      <DocSection title="2c. The web app">
        <p className="text-sm leading-relaxed text-muted">Requires Node 20 or newer.</p>
        <CodeBlock label="bash">{`cd RaceControlWeb
cp .env.example .env.local   # RACECONTROL_API_BASE_URL=http://localhost:8000
npm install
npm run dev`}</CodeBlock>
        <p className="text-sm leading-relaxed text-muted">
          Opens on <code className="font-mono text-xs text-foreground">http://localhost:3000</code>. With the
          backend running open (the <code className="font-mono text-xs text-foreground">./run.sh</code> default),
          leave <code className="font-mono text-xs text-foreground">RACECONTROL_API_TOKEN</code> empty.
        </p>
      </DocSection>

      <DocSection title="2d. This site">
        <p className="text-sm leading-relaxed text-muted">
          Requires Node 20 or newer. No backend needed — every external link falls back to a sensible default.
        </p>
        <CodeBlock label="bash">{`cd RaceControlSite
npm install
npm run dev`}</CodeBlock>
      </DocSection>
    </div>
  );
}
