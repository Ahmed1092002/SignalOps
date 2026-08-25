import { cn } from "@/lib/utils";

export function PhaseBadge({ phase, className }: { phase: string; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border border-border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground",
        className
      )}
    >
      {phase}
    </span>
  );
}
