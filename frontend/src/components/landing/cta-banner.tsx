import Link from "next/link";
import { ArrowRight, Radio, ShieldCheck, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";

export function LandingCtaBanner() {
  return (
    <section className="py-16">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-primary via-indigo-600 to-sky-600 p-8 sm:p-12 md:p-16 text-white shadow-2xl">
          {/* Subtle decorative circles */}
          <div
            className="pointer-events-none absolute -right-20 -top-20 h-96 w-96 rounded-full bg-white/10 blur-2xl"
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute -bottom-20 -left-20 h-96 w-96 rounded-full bg-black/15 blur-2xl"
            aria-hidden="true"
          />

          <div className="relative z-10 max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold backdrop-blur-md mb-4 text-white">
              <Sparkles className="h-3.5 w-3.5 text-amber-300" />
              <span>Ready for high-availability reliability?</span>
            </div>

            <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl md:text-5xl text-white">
              Take Full Command of Your Production Incidents Today.
            </h2>

            <p className="mt-4 text-base sm:text-lg text-white/85 leading-relaxed">
              Equip your engineering and SRE teams with the tooling needed to declare, coordinate,
              and resolve critical outages before they impact your business reputation.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-4">
              <Button
                size="lg"
                asChild
                className="h-12 px-6 text-base font-semibold bg-white text-indigo-900 hover:bg-white/90 shadow-lg"
              >
                <Link href="/register" className="gap-2">
                  <span>Create Free Organization</span>
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>
              <Button
                size="lg"
                variant="outline"
                asChild
                className="h-12 px-6 text-base font-semibold bg-white/10 hover:bg-white/20 text-white border-white/30 backdrop-blur-sm"
              >
                <Link href="/login" className="gap-2">
                  <Radio className="h-4 w-4 animate-pulse" />
                  <span>Launch Live Demo</span>
                </Link>
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
