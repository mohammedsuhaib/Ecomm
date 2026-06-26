'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { logout as apiLogout, staffLogin } from '@/app/lib/api';
import {
  clearAuth,
  getRefreshToken,
  getStoredUser,
  saveAuth,
} from '@/app/lib/auth';
import type { UserDto } from '@/app/lib/types';

interface AuthContextValue {
  user: UserDto | null;
  isAuthenticated: boolean;
  /** Whether the initial localStorage hydration has run (avoids login flash). */
  ready: boolean;
  /** Email+password staff login; resolves on success, throws on failure. */
  login: (email: string, password: string) => Promise<void>;
  /** Revoke the refresh token (best-effort) and clear the local session. */
  logout: () => Promise<void>;
  /** Force the context to re-read the store (e.g. after a 401 cleared auth). */
  refresh: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [ready, setReady] = useState(false);

  // Hydrate from localStorage on mount (client-only; SSR renders unauthed).
  useEffect(() => {
    setUser(getStoredUser());
    setReady(true);

    // Keep tabs in sync: another tab logging out / in updates this one.
    const onStorage = () => setUser(getStoredUser());
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await staffLogin(email, password);
    saveAuth({
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      user: auth.user,
    });
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        await apiLogout(refreshToken);
      } catch {
        // Idempotent server-side; revoke locally regardless.
      }
    }
    clearAuth();
    setUser(null);
  }, []);

  // Re-sync from the store after an external mutation (api.ts clears auth on an
  // unrecoverable 401). Components call this when they catch AuthRequiredError.
  const refresh = useCallback(() => {
    setUser(getStoredUser());
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user != null,
      ready,
      login,
      logout,
      refresh,
    }),
    [user, ready, login, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an <AuthProvider>');
  }
  return ctx;
}
