import type { Metadata } from "next";
import { DocHeading } from "@/components/CodeBlock";
import { ApiReference } from "@/components/ApiReference";
import { Card } from "@/components/Card";
import { siteConfig } from "@/lib/config";

export const metadata: Metadata = {
  title: "API reference — RaceControl docs",
  description: "Every RaceControl backend endpoint, searchable and grouped, with query parameters.",
};

export default function ApiDocsPage() {
  return (
    <div className="flex flex-col gap-8">
      <DocHeading
        title="API reference"
        intro="A thin FastAPI layer over FastF1, converting pandas timedeltas, timestamps and NaNs into JSON that's safe to parse and ready to chart. Every response is cached; the derived analytics endpoints return chart-ready payloads with team colours resolved and axis domains supplied."
      />

      <Card className="flex flex-col gap-3">
        <p className="text-sm leading-relaxed text-muted">
          <span className="font-medium text-foreground">Authentication. </span>
          Mobile clients authorise with device attestation (Apple App Attest, Google Play Integrity); the web app
          holds a server-side token in its BFF layer. Run the backend locally with no auth environment variables
          and it&apos;s open, which is how all the examples below behave.
        </p>
        <p className="text-sm leading-relaxed text-muted">
          <span className="font-medium text-foreground">Partial data. </span>
          The derived analytics endpoints return{" "}
          <code className="font-mono text-xs text-foreground">available: false</code> with a valid empty body
          rather than erroring when a session&apos;s data is incomplete.
        </p>
        <a
          href={siteConfig.backendDocsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="self-start rounded-md border border-border px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:border-foreground"
        >
          Open the generated Swagger docs ↗
        </a>
      </Card>

      <ApiReference />
    </div>
  );
}
