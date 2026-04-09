'use client';

import { useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api';
import type { User } from '@/types';

const AUTH_QUERY_KEY = ['auth', 'me'] as const;

/**
 * 401 = kesin oturum yok (null don)
 * Diger hatalar = ag sorunu (throw et, React Query retry etsin)
 */
async function fetchMe(): Promise<User | null> {
  try {
    return await apiClient.getMe();
  } catch (err: unknown) {
    // ApiError.status === 401 → oturum kesin yok
    if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 401) {
      return null;
    }
    // Diger hatalar (ag, timeout, 500 vs) → throw et, retry yapilsin
    throw err;
  }
}

export function useAuth() {
  const queryClient = useQueryClient();

  const {
    data: user,
    isLoading,
    isFetched,
    isError,
  } = useQuery<User | null>({
    queryKey: AUTH_QUERY_KEY,
    queryFn: fetchMe,
    staleTime: 5 * 60 * 1000,
    retry: 2,
    retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 5000),
  });

  const login = useCallback(
    async (email: string, password: string) => {
      const raw = await apiClient.login(email, password);
      if (!raw || typeof raw !== 'object') {
        throw new Error('Login yaniti bos veya gecersiz');
      }
      const loggedInUser: User = {
        id: (raw as Record<string, unknown>).id as number ?? 0,
        email: (raw as Record<string, unknown>).email as string ?? email,
        displayName: ((raw as Record<string, unknown>).display_name as string) || '',
        role: ((raw as Record<string, unknown>).role as string) || 'user',
      };
      queryClient.setQueryData<User | null>(AUTH_QUERY_KEY, loggedInUser);
      return loggedInUser;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await apiClient.logout();
    } finally {
      queryClient.setQueryData<User | null>(AUTH_QUERY_KEY, null);
      queryClient.removeQueries({ queryKey: AUTH_QUERY_KEY });
    }
  }, [queryClient]);

  const checkAuth = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: AUTH_QUERY_KEY });
  }, [queryClient]);

  // isChecked: auth sorgusu tamamlandi VE sonuc kesin (null=401 veya user objesi)
  // isError durumunda henuz kesin degiliz (retry devam ediyor olabilir)
  const isChecked = isFetched && !isError;

  return {
    user: user ?? null,
    isAuthenticated: !!user,
    isLoading,
    isChecked,
    login,
    logout,
    checkAuth,
  };
}
