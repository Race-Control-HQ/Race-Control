import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/Card";
import { ENDPOINTS } from "@/lib/endpoints";
import { FEATURES } from "@/lib/features";
import { siteConfig } from "@/lib/config";

export const metadata: Metadata = {
  title: "Documentation — RaceControl",
  description: "Run RaceControl locally, browse the API reference, understand the architecture, and self-host it.",
};

const CARDS = [
  {
    href: "/docs/getting-started",
    title: "Run it locally",
    desc: "Get the backend and any of the three clients running on your own machine, in the right order.",
  },
  {
    href: "/docs/api",
    title: "API reference",
    desc: `All ${ENDPOINTS.length} endpoints, searchable and grouped, with their query parameters.`,
  },
  {
    href: "/docs/architecture",
    title: "Architecture",
    desc: "How one FastAPI service feeds three independent clients, and why the web app needs a BFF layer.",
  },
  {
    href: "/docs/self-hosting",
    title: "Self-hosting",
    desc: "Deploy the whole stack to Coolify: services, environment variables, volumes and the proxy trust boundary.",
  },
];

export default function DocsPage() {
  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-racing-red-text">Documentation</p>
        <h1 className="text-3xl font-bold tracking-tight">Documentation</h1>
        <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted">
          RaceControl is {FEATURES.length} features across three clients, backed by one Python service exposing{" "}
          {ENDPOINTS.length} REST endpoints over {new Date().getFullYear() - 2018}+ seasons of Formula 1 data. Start
          here, whether you want to run it, read the API, or host your own copy.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {CARDS.map((card) => (
          <Link key={card.href} href={card.href}>
            <Card className="h-full transition-colors hover:border-foreground">
              <h2 className="text-base font-semibold">{card.title}</h2>
              <p className="mt-1.5 text-sm leading-relaxed text-muted">{card.desc}</p>
            </Card>
          </Link>
        ))}
      </div>

      <Card>
        <h2 className="text-base font-semibold">Deeper reading, in the repository</h2>
        <p className="mt-1.5 text-sm leading-relaxed text-muted">
          Some documentation is long-form and lives with the code, where it can be reviewed alongside changes.
        </p>
        <ul className="mt-3 flex flex-col gap-2 text-sm">
          <li>
            <ExternalDoc
              href={siteConfig.githubRepo + "/blob/main/RaceControlAndroid/docs/FEATURES.md"}
              title="Feature inventory (FEATURES.md)"
              desc="The screen-by-screen iOS to Android mapping, with every deliberate platform divergence and the reasoning behind it."
            />
          </li>
          <li>
            <ExternalDoc
              href={siteConfig.githubRepo + "/blob/main/docs/VISUAL_PARITY.md"}
              title="Cross-platform parity checklist (VISUAL_PARITY.md)"
              desc="The manual checklist used before a visualization-touching release, covering loading, empty, error and accessibility states on all three stacks."
            />
          </li>
          <li>
            <ExternalDoc
              href={siteConfig.backendDocsUrl}
              title="Swagger / OpenAPI"
              desc="Generated request and response schemas for every endpoint, testable in the browser against the live backend."
            />
          </li>
        </ul>
      </Card>
    </div>
  );
}

function ExternalDoc({ href, title, desc }: { href: string; title: string; desc: string }) {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className="group block">
      <span className="font-medium text-foreground underline decoration-border underline-offset-2 group-hover:decoration-foreground">
        {title} ↗
      </span>
      <span className="mt-0.5 block text-sm leading-relaxed text-muted">{desc}</span>
    </a>
  );
}
