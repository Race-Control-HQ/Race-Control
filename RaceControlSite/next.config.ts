import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async redirects() {
    return [
      // /backend was an early page; its content now lives in the docs section.
      { source: "/backend", destination: "/docs/api", permanent: true },
    ];
  },
};

export default nextConfig;
