'use client';

import { useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ThemeProvider from '@/theme/ThemeProvider';
import { useWebSocketInit } from '@/hooks/useWebSocket';

interface Props {
  children: ReactNode;
}

function WebSocketGlobal() {
  useWebSocketInit();
  return null;
}

export default function Providers({ children }: Props) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 2,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <WebSocketGlobal />
        {children}
      </ThemeProvider>
    </QueryClientProvider>
  );
}
