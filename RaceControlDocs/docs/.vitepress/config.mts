import { defineConfig } from "vitepress";

const repo = "https://github.com/Race-Control-HQ/Race-Control";

const sidebar = [
  { text: "Home", link: "/" },
  {
    text: "Getting Started",
    link: "/getting-started/index",
    items: [
      { text: "Run the Backend", link: "/getting-started/backend" },
      { text: "Run the iOS App", link: "/getting-started/ios" },
      { text: "Run the Android App", link: "/getting-started/android" },
      { text: "Run the Web App", link: "/getting-started/web" },
      { text: "Run the Site & Docs", link: "/getting-started/site-and-docs" },
    ],
  },
  {
    text: "Features",
    link: "/features/index",
    items: [
      { text: "Race Analysis", link: "/features/analysis" },
      { text: "Platform Differences", link: "/features/platforms" },
    ],
  },
  {
    text: "API Reference",
    link: "/api/index",
    items: [
      { text: "Core Endpoints", link: "/api/endpoints" },
      { text: "Derived Analytics", link: "/api/analytics" },
      { text: "Authentication Endpoints", link: "/api/authentication" },
    ],
  },
  {
    text: "Architecture",
    link: "/architecture/index",
    items: [
      { text: "Backend", link: "/architecture/backend" },
      { text: "iOS App", link: "/architecture/ios" },
      { text: "Android App", link: "/architecture/android" },
      { text: "Web App (BFF)", link: "/architecture/web" },
      { text: "Device Attestation", link: "/architecture/authentication" },
      { text: "Design System", link: "/architecture/design-system" },
      {
        text: "Android Feature Inventory",
        link: "/architecture/android-feature-inventory",
      },
    ],
  },
  {
    text: "Self-Hosting",
    link: "/self-hosting/index",
    items: [
      { text: "Backend", link: "/self-hosting/backend" },
      { text: "Web App", link: "/self-hosting/web" },
      { text: "Site & Docs", link: "/self-hosting/site-and-docs" },
      { text: "Device Attestation Setup", link: "/self-hosting/attestation" },
      { text: "Environment Variables", link: "/self-hosting/environment" },
    ],
  },
  {
    text: "Contributing",
    link: "/contributing/index",
    items: [
      { text: "Development Setup", link: "/contributing/development" },
      { text: "Pull Requests", link: "/contributing/pull-requests" },
      { text: "Continuous Integration", link: "/contributing/ci" },
      { text: "Visual Parity Checklist", link: "/contributing/visual-parity" },
      { text: "Writing Documentation", link: "/contributing/documentation" },
      { text: "Android Build Plan", link: "/contributing/android-build-plan" },
      { text: "Android Status", link: "/contributing/android-status" },
      { text: "Code of Conduct", link: "/contributing/code-of-conduct" },
      { text: "Security Policy", link: "/contributing/security" },
    ],
  },
  {
    text: "Help",
    link: "/help/index",
    items: [
      { text: "Troubleshooting", link: "/help/troubleshooting" },
      { text: "Common Questions", link: "/help/faq" },
      { text: "Getting Support", link: "/help/support" },
    ],
  },
];

export default defineConfig({
  title: "RaceControl",
  description:
    "Documentation for RaceControl: native iOS, Android and web apps for exploring Formula 1 data, and the FastF1 backend behind them.",
  lang: "en-GB",
  cleanUrls: true,
  // Bare localhost URLs in the setup guides are addresses to open, not links to check.
  ignoreDeadLinks: "localhostLinks",
  head: [
    ["link", { rel: "icon", type: "image/png", href: "/favicon.png" }],
    ["meta", { name: "theme-color", content: "#e10600" }],
  ],
  themeConfig: {
    logo: "/logo.png",
    search: {
      provider: "local",
    },
    nav: [
      { text: "Getting Started", link: "/getting-started/index" },
      { text: "Features", link: "/features/index" },
      { text: "API", link: "/api/index" },
      { text: "Self-Hosting", link: "/self-hosting/index" },
      { text: "Contributing", link: "/contributing/index" },
      {
        text: "More",
        items: [
          { text: "Architecture", link: "/architecture/index" },
          { text: "Help", link: "/help/index" },
          { text: "Discussions", link: `${repo}/discussions` },
          { text: "getracecontrol.com", link: "https://getracecontrol.com" },
        ],
      },
    ],
    sidebar: {
      "/": sidebar,
    },
    socialLinks: [{ icon: "github", link: repo }],
    editLink: {
      pattern: `${repo}/edit/main/RaceControlDocs/docs/:path`,
      text: "Edit this page on GitHub",
    },
    outline: { level: [2, 3] },
    footer: {
      message:
        "RaceControl is unofficial and is not associated with the Formula 1 companies. F1, FORMULA 1 and related marks are trademarks of Formula One Licensing BV.",
      copyright: "Released under the MIT License. © 2026 Owl Media",
    },
  },
});
