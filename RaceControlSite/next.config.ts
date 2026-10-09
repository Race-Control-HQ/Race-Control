import type { NextConfig } from "next";

// Documentation moved off this site to the VitePress docs (../RaceControlDocs).
// Kept in step with siteConfig.docsUrl in src/lib/config.ts.
const DOCS_URL = (process.env.NEXT_PUBLIC_DOCS_URL ?? "https://docs.getracecontrol.com").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  async redirects() {
    return [
      // The old in-site docs section, page by page, then a catch-all.
      { source: "/docs", destination: DOCS_URL, permanent: true },
      { source: "/docs/getting-started", destination: `${DOCS_URL}/getting-started/`, permanent: true },
      { source: "/docs/api", destination: `${DOCS_URL}/api/`, permanent: true },
      { source: "/docs/architecture", destination: `${DOCS_URL}/architecture/`, permanent: true },
      { source: "/docs/self-hosting", destination: `${DOCS_URL}/self-hosting/`, permanent: true },
      { source: "/docs/:path*", destination: DOCS_URL, permanent: true },
      // /backend was an even earlier page for the API reference.
      { source: "/backend", destination: `${DOCS_URL}/api/`, permanent: true },
    ];
  },
};

export default nextConfig;
