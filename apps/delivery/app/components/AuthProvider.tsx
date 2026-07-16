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
import { apiLogout, staffLogin } from '@/app/lib/api';
import { clearAuth, getRefreshToken, getStoredUser, saveAuth } from '@/app/lib/auth';
import type { UserDto } from '@/app/lib/types';

interface AuthCtx {
  user: UserDto | null;
  isAuthenticated: boolean;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => void;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setUser(getStoredUser());
    setReady(true);
    const onStorage = () => setUser(getStoredUser());
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await staffLogin(email, password);
    // Delivery agents must have DELIVERY_AGENT (or ADMIN) role.
    if (auth.user.role !== 'DELIVERY_AGENT' && auth.user.role !== 'ADMIN') {
      throw new Error('This account does not have delivery access.');
    }
    saveAuth({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user });
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    const rt = getRefreshToken();
    if (rt) { try { await apiLogout(rt); } catch { /* ignore */ } }
    clearAuth();
    setUser(null);
  }, []);

  const refresh = useCallback(() => setUser(getStoredUser()), []);

  const value = useMemo<AuthCtx>(
    () => ({ user, isAuthenticated: user != null, ready, login, logout, refresh }),
    [user, ready, login, logout, refresh],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
