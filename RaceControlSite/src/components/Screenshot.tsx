import Image from "next/image";
import clsx from "clsx";
import { SCREENSHOTS, type ScreenshotFrame, type ScreenshotId } from "@/lib/screenshots";

export function Screenshot({
  id,
  className,
  priority = false,
}: {
  id: ScreenshotId;
  className?: string;
  priority?: boolean;
}) {
  const meta = SCREENSHOTS[id];

  return (
    <figure className={clsx("flex flex-col gap-2", className)}>
      <Frame kind={meta.frame}>
        <Image
          src={meta.src}
          width={meta.width}
          height={meta.height}
          alt={meta.alt}
          priority={priority}
          sizes={meta.frame === "phone" ? "(max-width: 640px) 50vw, 280px" : "(max-width: 1024px) 100vw, 520px"}
          className="block h-auto w-full"
        />
      </Frame>
      <figcaption className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
        <span className="font-medium text-foreground">{meta.caption}</span>
        <span className="rounded-full border border-border bg-surface-raised px-2 py-0.5 text-[11px] text-muted">
          {meta.platform} capture
        </span>
      </figcaption>
    </figure>
  );
}

function Frame({ kind, children }: { kind: ScreenshotFrame; children: React.ReactNode }) {
  if (kind === "browser") {
    return (
      <div className="overflow-hidden rounded-xl border border-border bg-surface">
        <div className="flex items-center gap-1.5 border-b border-border bg-surface-raised px-3 py-2">
          <span className="h-2 w-2 rounded-full bg-border" />
          <span className="h-2 w-2 rounded-full bg-border" />
          <span className="h-2 w-2 rounded-full bg-border" />
        </div>
        {children}
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[280px] overflow-hidden rounded-[28px] border-[6px] border-surface-raised bg-background ring-1 ring-border">
      {children}
    </div>
  );
}
