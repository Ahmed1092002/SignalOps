"use client";

import { AlertTriangle } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

export function ConflictDialog({
  open,
  currentVersion,
  onReload,
  onKeep,
}: {
  open: boolean;
  currentVersion: number | null;
  onReload: () => void;
  onKeep: () => void;
}) {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-severity-medium" aria-hidden="true" />
            This incident was updated by someone else
          </AlertDialogTitle>
          <AlertDialogDescription>
            Your change is based on version {currentVersion ?? "an older"} of this incident and was
            not saved. Load the latest data, or keep your text to reapply it manually.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onKeep}>Keep my text</AlertDialogCancel>
          <AlertDialogAction onClick={onReload}>Load latest</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
