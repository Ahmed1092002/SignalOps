import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export function StatCard({
  label,
  value,
  hint,
  tone = "default",
  icon,
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: "default" | "critical" | "success" | "warning";
  icon?: React.ReactNode;
}) {
  const toneColor =
    tone === "critical"
      ? "text-severity-critical"
      : tone === "success"
        ? "text-svc-operational"
        : tone === "warning"
          ? "text-severity-medium"
          : "text-foreground";
  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{label}</p>
          {icon && <span className="text-muted-foreground">{icon}</span>}
        </div>
        <p className={cn("mt-2 text-3xl font-semibold text-num", toneColor)}>{value}</p>
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}
