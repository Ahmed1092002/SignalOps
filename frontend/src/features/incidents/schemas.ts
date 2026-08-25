import { z } from "zod";

export const createIncidentSchema = z
  .object({
    title: z
      .string()
      .trim()
      .min(3, "Title must be at least 3 characters.")
      .max(200, "Title must be at most 200 characters."),
    description: z.string().trim().max(10000, "Description is too long.").optional().or(z.literal("")),
    severity: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
    serviceIds: z.array(z.string()).min(1, "Select at least one affected service."),
    commanderUserId: z.string().uuid().nullable().optional(),
    responderUserIds: z.array(z.string()).optional(),
  })
  .superRefine((value, ctx) => {
    if (value.severity === "CRITICAL" && !value.commanderUserId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["commanderUserId"],
        message: "A Critical incident requires an Incident Commander.",
      });
    }
  });

export type CreateIncidentInput = z.infer<typeof createIncidentSchema>;

export type ServiceInput = z.infer<typeof serviceSchema>;
export type InviteInput = z.infer<typeof inviteSchema>;
export type ProfileInput = z.infer<typeof profileSchema>;

export const transitionSchema = z
  .object({
    to: z.enum(["DETECTED", "INVESTIGATING", "IDENTIFIED", "MONITORING", "RESOLVED"]),
    resolutionSummary: z.string().trim().max(10000).optional(),
    reason: z.string().trim().max(2000).optional(),
  })
  .superRefine((value, ctx) => {
    if (value.to === "RESOLVED" && !value.resolutionSummary) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["resolutionSummary"],
        message: "A resolution summary is required to resolve an incident.",
      });
    }
    if (value.to === "INVESTIGATING" && !value.reason) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["reason"],
        message: "A reason is required for this transition.",
      });
    }
  });

export const commentSchema = z
  .string()
  .trim()
  .min(1, "Write something before posting.")
  .max(10000, "Comment is too long.");

export const publicUpdateSchema = z
  .string()
  .trim()
  .min(1, "Write the customer-facing message first.")
  .max(10000, "Update is too long.");

export const serviceSchema = z.object({
  name: z.string().trim().min(2, "Name must be at least 2 characters.").max(80),
  description: z.string().trim().max(2000).optional().or(z.literal("")),
  owningTeam: z.string().trim().max(80).optional().or(z.literal("")),
  isPublic: z.boolean().optional(),
});

export const inviteSchema = z.object({
  email: z.string().trim().email("Enter a valid email address."),
  role: z.enum(["ADMIN", "INCIDENT_COMMANDER", "RESPONDER", "VIEWER"]),
});

export const profileSchema = z
  .object({
    fullName: z.string().trim().min(1, "Name is required.").max(120).optional(),
    currentPassword: z.string().optional(),
    newPassword: z
      .string()
      .regex(/^(?=.*\p{L})(?=.*\d).{10,72}$/u, "Use 10-72 characters with at least one letter and one digit.")
      .optional()
      .or(z.literal("")),
  })
  .superRefine((value, ctx) => {
    if (value.newPassword && !value.currentPassword) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["currentPassword"],
        message: "Enter your current password to set a new one.",
      });
    }
  });
