import React from "react";
import { cn } from "@/lib/utils";

interface LogoProps extends React.SVGProps<SVGSVGElement> {
  className?: string;
  size?: number | string;
  withPulse?: boolean;
}

/**
 * SignalOps standalone icon/mark.
 * Incorporates telemetry radar rings, active nodes, and glowing signal waveform.
 */
export function SignalOpsMark({ className, size = 32, withPulse = false, ...props }: LogoProps) {
  return (
    <svg
      viewBox="0 0 48 48"
      fill="none"
      width={size}
      height={size}
      className={cn("shrink-0 select-none overflow-visible", className)}
      aria-hidden="true"
      {...props}
    >
      <defs>
        <linearGradient id="sigmark-bg" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#4F46E5" />
          <stop offset="50%" stopColor="#6366F1" />
          <stop offset="100%" stopColor="#0EA5E9" />
        </linearGradient>
        <linearGradient id="sigmark-pulse" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#38BDF8" stopOpacity="0.9" />
          <stop offset="100%" stopColor="#818CF8" stopOpacity="0.4" />
        </linearGradient>
        <filter id="sigmark-glow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="1.5" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>

      {/* Rounded container with glossy gradient */}
      <rect width="48" height="48" rx="13" fill="url(#sigmark-bg)" />
      <rect
        width="48"
        height="48"
        rx="13"
        stroke="rgba(255, 255, 255, 0.25)"
        strokeWidth="1.2"
      />

      {/* Radar rings */}
      <circle
        cx="24"
        cy="24"
        r="17"
        stroke="white"
        strokeOpacity="0.15"
        strokeWidth="1"
        strokeDasharray="2 3"
      />
      <circle cx="24" cy="24" r="10" stroke="white" strokeOpacity="0.18" strokeWidth="1" />

      {/* Pulse waveform */}
      <path
        d="M 8 24.5 L 15 24.5 L 19 12 L 25 36 L 29 18 L 33 24.5 L 40 24.5"
        stroke="url(#sigmark-pulse)"
        strokeWidth="3.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        filter="url(#sigmark-glow)"
        className={withPulse ? "animate-pulse" : undefined}
      />
      <path
        d="M 8 24.5 L 15 24.5 L 19 12 L 25 36 L 29 18 L 33 24.5 L 40 24.5"
        stroke="#FFFFFF"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Telemetry nodes */}
      <circle cx="19" cy="12" r="2.2" fill="#38BDF8" stroke="#FFFFFF" strokeWidth="1" />
      <circle cx="25" cy="36" r="2.2" fill="#F43F5E" stroke="#FFFFFF" strokeWidth="1" />
      <circle cx="29" cy="18" r="2" fill="#4ADE80" stroke="#FFFFFF" strokeWidth="0.8" />
    </svg>
  );
}

interface FullLogoProps {
  className?: string;
  iconSize?: number | string;
  showSubtitle?: boolean;
  withPulse?: boolean;
}

/**
 * SignalOps Full Horizontal Logo with mark and brand typography.
 */
export function SignalOpsLogo({
  className,
  iconSize = 36,
  showSubtitle = true,
  withPulse = false,
}: FullLogoProps) {
  return (
    <div className={cn("inline-flex items-center gap-2.5 select-none", className)}>
      <SignalOpsMark size={iconSize} withPulse={withPulse} />
      <div className="flex flex-col leading-none">
        <div className="flex items-center tracking-tight font-extrabold text-foreground text-lg md:text-xl">
          <span>Signal</span>
          <span className="bg-gradient-to-r from-primary to-sky-400 bg-clip-text text-transparent ml-0.5">
            Ops
          </span>
        </div>
        {showSubtitle && (
          <span className="text-[10px] uppercase font-semibold tracking-widest text-primary/80 mt-0.5">
            Incident Management
          </span>
        )}
      </div>
    </div>
  );
}
