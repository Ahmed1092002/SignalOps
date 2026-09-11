import { LandingNavbar } from "@/components/landing/navbar";
import { LandingHero } from "@/components/landing/hero";
import { LandingFeatures } from "@/components/landing/features";
import { LandingLifecycle } from "@/components/landing/incident-lifecycle";
import { LandingProductTour } from "@/components/landing/product-tour";
import { LandingTechStack } from "@/components/landing/tech-stack";
import { LandingDemoCredentials } from "@/components/landing/demo-credentials";
import { LandingFAQ } from "@/components/landing/faq";
import { LandingCtaBanner } from "@/components/landing/cta-banner";
import { LandingFooter } from "@/components/landing/footer";

export const metadata = {
  title: "SignalOps · Real-Time Incident Management Platform",
  description:
    "Declare incidents, enforce controlled state machines, coordinate responders with zero-latency STOMP WebSockets, and broadcast customer-safe status pages.",
};

export default function LandingPage() {
  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground selection:bg-primary/20 selection:text-primary">
      <LandingNavbar />
      <main className="flex-1">
        <LandingHero />
        <LandingFeatures />
        <LandingLifecycle />
        <LandingProductTour />
        <LandingTechStack />
        <LandingDemoCredentials />
        <LandingFAQ />
        <LandingCtaBanner />
      </main>
      <LandingFooter />
    </div>
  );
}
