"use client";

import { useState } from "react";
import { ChevronDown, HelpCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";

const FAQS = [
  {
    q: "How does SignalOps enforce tenant isolation?",
    a: "SignalOps enforces isolation at the database query layer, foreign key constraints, and application authorization. Any attempt to access resources outside your active organization returns an indistinguishable HTTP 404, preventing tenant enumeration.",
  },
  {
    q: "How are real-time updates broadcast to responders?",
    a: "SignalOps utilizes STOMP over WebSockets. Once a transactional database commit occurs in PostgreSQL, events are emitted to organization-scoped destination topics (`/topic/incidents/{orgId}`). Responders see updates, comments, and state changes in sub-50 milliseconds.",
  },
  {
    q: "How does the public status page prevent sensitive internal data leaks?",
    a: "The public status page is completely decoupled from the internal incident war room. Service statuses are calculated automatically (the worst active incident severity sets the service degradation), and only explicitly published customer updates are visible to external visitors.",
  },
  {
    q: "What happens if two responders update an incident concurrently?",
    a: "SignalOps implements optimistic locking via version numbers. If a stale update is submitted, the backend rejects it with an HTTP 409 STALE_WRITE and prompts the user to refresh the latest state without losing uncommitted input.",
  },
  {
    q: "Can I host SignalOps in my own VPC or infrastructure?",
    a: "Yes. SignalOps is packaged with Docker Compose and can be deployed to AWS ECS/EKS, Google Cloud Run/GKE, or bare-metal Kubernetes using standard Spring Boot and PostgreSQL containers.",
  },
];

export function LandingFAQ() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);

  const toggle = (idx: number) => {
    setOpenIndex(openIndex === idx ? null : idx);
  };

  return (
    <section id="faq" className="py-20">
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge variant="outline" className="mb-3 border-primary/30 text-primary bg-primary/5">
            Got Questions?
          </Badge>
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-foreground">
            Frequently Asked Questions
          </h2>
          <p className="mt-3 text-base text-muted-foreground">
            Everything you need to know about SignalOps architecture, reliability, and security.
          </p>
        </div>

        {/* FAQ Accordion List */}
        <div className="space-y-3">
          {FAQS.map((item, idx) => {
            const isOpen = openIndex === idx;
            return (
              <div
                key={idx}
                className="rounded-xl border border-border/80 bg-card overflow-hidden transition-colors"
              >
                <button
                  type="button"
                  onClick={() => toggle(idx)}
                  className="w-full flex items-center justify-between p-5 text-left font-semibold text-sm sm:text-base text-foreground hover:bg-accent/40 transition-colors"
                >
                  <span className="flex items-center gap-3">
                    <HelpCircle className="h-4 w-4 text-primary shrink-0" />
                    <span>{item.q}</span>
                  </span>
                  <ChevronDown
                    className={`h-4 w-4 text-muted-foreground transition-transform duration-200 shrink-0 ml-2 ${
                      isOpen ? "rotate-180 text-primary" : ""
                    }`}
                  />
                </button>
                {isOpen && (
                  <div className="px-5 pb-5 pt-1 text-sm text-muted-foreground leading-relaxed border-t border-border/40 animate-in fade-in duration-200">
                    {item.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
