'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { apiLogout, staffLogin } from '@/app/lib/api';
import { clearAuth, getRefreshToken, getStoredUser, saveAuth } from '@/app/lib/auth';
import { unsubscribeCurrentBrowser } from '@/app/lib/push';
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
  // Whether a rider was signed in as far as this provider knows. Read by the
  // re-reads below to tell "the session just ended" (stop the alerts) from
  // "nobody was signed in anyway" (nothing to do).
  const signedIn = useRef(false);

  useEffect(() => {
    signedIn.current = user != null;
  }, [user]);

  // Re-read the stored session. Called on a foreign 401 (api.ts has already
  // cleared the tokens by then) and when another tab changes storage, so this
  // is where an EXPIRED session lands — no logout() runs for that path, and a
  // phone left subscribed would go on buzzing for a rider who can no longer
  // open the app.
  const syncStoredUser = useCallback(() => {
    const next = getStoredUser();
    if (next == null && signedIn.current) {
      void unsubscribeCurrentBrowser().catch(() => undefined);
    }
    setUser(next);
  }, []);

  useEffect(() => {
    setUser(getStoredUser());
    setReady(true);
    window.addEventListener('storage', syncStoredUser);
    return () => window.removeEventListener('storage', syncStoredUser);
  }, [syncStoredUser]);

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
    // Stop the alerts for the rider who just left. The subscription is stored
    // against their account, so a phone that keeps it goes on announcing their
    // deliveries to whoever is holding it. Best-effort and not awaited: signing
    // out must never be blocked by a push service. The DELETE is authorised by
    // the endpoint URL itself (SecurityConfig), so it still works with the
    // token already gone.
    void unsubscribeCurrentBrowser().catch(() => undefined);
  }, []);

  const value = useMemo<AuthCtx>(
    () => ({ user, isAuthenticated: user != null, ready, login, logout, refresh: syncStoredUser }),
    [user, ready, login, logout, syncStoredUser],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
