import type { Metadata } from "next";
import { SectionHeading } from "@/components/Card";
import { FeatureCatalogue } from "@/components/FeatureCatalogue";
import { Screenshot } from "@/components/Screenshot";
import { FEATURES } from "@/lib/features";

export const metadata: Metadata = {
  title: "Features — RaceControl",
  description:
    "Every RaceControl feature, screen by screen: race replay, telemetry, tyre strategy, flags, race control log, standings, circuits and championship analysis, with platform availability for each.",
};

export default function FeaturesPage() {
  return (
    <div className="flex flex-col gap-12">
      <SectionHeading
        eyebrow="Features"
        title={`${FEATURES.length} features, screen by screen`}
        subtitle="Every screen in the app, what's on it, and which platforms have it. Where a platform deliberately differs, that's called out rather than hidden — the full reasoning for each divergence lives in the Android app's FEATURES.md."
      />

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <Screenshot id="webDashboard" />
        <Screenshot id="webLapTimes" />
        <Screenshot id="webDrivers" />
        <Screenshot id="iosTelemetry" priority />
      </div>

      <FeatureCatalogue />
    </div>
  );
}
