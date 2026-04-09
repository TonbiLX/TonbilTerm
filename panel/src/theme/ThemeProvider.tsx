'use client';

import { type ReactNode, useEffect, useMemo } from 'react';
import { ThemeProvider as MuiThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { lightTheme, darkTheme } from './theme';
import { useUiStore } from '@/stores/uiStore';
import type { ThemeMode } from '@/types';

function useResolvedTheme(mode: ThemeMode) {
  useEffect(() => {
    if (mode !== 'system') return;

    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => useUiStore.getState().forceRerender();
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [mode]);

  return useMemo(() => {
    if (mode === 'light') return lightTheme;
    if (mode === 'dark') return darkTheme;
    if (typeof window === 'undefined') return lightTheme;
    return window.matchMedia('(prefers-color-scheme: dark)').matches
      ? darkTheme
      : lightTheme;
  }, [mode]);
}

interface Props {
  children: ReactNode;
}

export default function ThemeProvider({ children }: Props) {
  const themeMode = useUiStore((s) => s.theme);
  const resolvedTheme = useResolvedTheme(themeMode);

  useEffect(() => {
    const saved = localStorage.getItem('tonbilterm-theme') as ThemeMode | null;
    if (saved) {
      useUiStore.getState().setTheme(saved);
    }
  }, []);

  return (
    <MuiThemeProvider theme={resolvedTheme}>
      <CssBaseline />
      <style jsx global>{`
        *, *::before, *::after {
          transition-property: background-color, border-color, color, fill, stroke;
          transition-duration: 0.25s;
          transition-timing-function: ease-in-out;
        }
      `}</style>
      {children}
    </MuiThemeProvider>
  );
}
