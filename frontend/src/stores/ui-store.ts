"use client";

import { create } from "zustand";

interface UiState {
  sidebarOpen: boolean;
  createIncidentOpen: boolean;
  setSidebarOpen: (open: boolean) => void;
  setCreateIncidentOpen: (open: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  sidebarOpen: false,
  createIncidentOpen: false,
  setSidebarOpen: (open) => set({ sidebarOpen: open }),
  setCreateIncidentOpen: (open) => set({ createIncidentOpen: open }),
}));
