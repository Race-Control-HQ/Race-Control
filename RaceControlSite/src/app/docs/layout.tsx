import { DocsNav } from "@/components/DocsNav";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-8 lg:flex-row lg:gap-10">
      <aside className="lg:w-52 lg:shrink-0">
        <div className="lg:sticky lg:top-20">
          <DocsNav />
        </div>
      </aside>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
