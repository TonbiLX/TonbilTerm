import { create } from 'zustand';
import type { ThemeMode, Alert } from '@/types';

interface UiState {
  theme: ThemeMode;
  selectedRoom: string | null;
  sidebarOpen: boolean;
  _rerenderKey: number;
  // Alerts (bildirimler)
  alerts: Alert[];
  dismissedAlerts: string[];
  notificationsOpen: boolean;
  setTheme: (theme: ThemeMode) => void;
  setSelectedRoom: (roomId: string | null) => void;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  forceRerender: () => void;
  setAlerts: (alerts: Alert[]) => void;
  dismissAlert: (id: string) => void;
  setNotificationsOpen: (open: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  theme: 'system',
  selectedRoom: null,
  sidebarOpen: false,
  _rerenderKey: 0,
  alerts: [],
  dismissedAlerts: JSON.parse(typeof window !== 'undefined' ? localStorage.getItem('dismissed_alerts') || '[]' : '[]'),
  notificationsOpen: false,

  setTheme: (theme) => {
    localStorage.setItem('tonbilterm-theme', theme);
    set({ theme });
  },

  setSelectedRoom: (roomId) => set({ selectedRoom: roomId }),

  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),

  setSidebarOpen: (open) => set({ sidebarOpen: open }),

  forceRerender: () => set((s) => ({ _rerenderKey: s._rerenderKey + 1 })),

  setAlerts: (alerts) => set({ alerts }),

  dismissAlert: (id) =>
    set((s) => {
      const updated = [...s.dismissedAlerts, id];
      localStorage.setItem('dismissed_alerts', JSON.stringify(updated));
      return { dismissedAlerts: updated };
    }),

  setNotificationsOpen: (open) => set({ notificationsOpen: open }),
}));
