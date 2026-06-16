'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { getMe, logout as logoutApi, phoneVerify } from '@/app/lib/api';
import {
  clearAuth,
  loadAuth,
  loadRefreshToken,
  saveAuth,
  saveUser,
} from '@/app/lib/auth';
import type { UserDto } from '@/app/lib/types';

// Auth state shared across the storefront shell: the header link, the login
// page (which also runs cart-merge), the account area, and checkout prefill all
// read through this context. Tokens themselves are kept in localStorage (see
// app/lib/auth.ts for the XSS trade-off note); this context exposes only the
// user + actions, never the raw tokens.
interface AuthContextValue {
  user: UserDto | null;
  isAuthenticated: boolean;
  /** Verify the phone+code, store tokens+user. Returns the signed-in user. */
  loginWithPhone: (phone: string, otp: string) => Promise<UserDto>;
  /** Revoke the refresh token server-side and clear the local session. */
  logout: () => Promise<void>;
  /** Re-fetch GET /me and refresh the cached user. */
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}

export default function AuthProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [user, setUser] = useState<UserDto | null>(null);

  // Hydrate the cached user on mount; keep in sync across tabs and same-tab
  // updates (login/logout/refresh all dispatch `tb:auth-changed`).
  useEffect(() => {
    setUser(loadAuth()?.user ?? null);
    const sync = () => setUser(loadAuth()?.user ?? null);
    window.addEventListener('tb:auth-changed', sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener('tb:auth-changed', sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  const loginWithPhone = useCallback(
    async (phone: string, otp: string): Promise<UserDto> => {
      // ---- Firebase seam (production) -----------------------------------
      // In production, with NEXT_PUBLIC_FIREBASE_* configured, this is where
      // the real Firebase Web SDK would run the phone-OTP flow and hand us a
      // *real* ID token to send to the backend, e.g.:
      //
      //   if (process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID) {
      //     // import { getAuth, signInWithPhoneNumber } from 'firebase/auth';
      //     // const confirmation = await signInWithPhoneNumber(auth, '+91'+phone, recaptcha);
      //     // const cred = await confirmation.confirm(otp);
      //     // firebaseIdToken = await cred.user.getIdToken();
      //   }
      //
      // The `firebase` npm package is intentionally NOT a dependency yet (no
      // keys; keeps the pnpm lockfile/CI stable per M4_CONTRACT §9). Until the
      // seam above is wired, we always use the backend's dev/fake verifier,
      // which accepts tokens of the form `dev:<phone>`. The OTP is validated
      // cosmetically by the login UI (any 6-digit code) and is not sent in dev.
      void otp; // cosmetic in dev; consumed by the real Firebase SDK in prod
      const firebaseIdToken = `dev:${phone}`;

      const res = await phoneVerify(firebaseIdToken);
      saveAuth({
        accessToken: res.accessToken,
        refreshToken: res.refreshToken,
        user: res.user,
      });
      setUser(res.user);
      return res.user;
    },
    [],
  );

  const logout = useCallback(async (): Promise<void> => {
    const refresh = loadRefreshToken();
    try {
      if (refresh) await logoutApi(refresh); // idempotent → 204
    } catch {
      /* best-effort revoke; clear locally regardless */
    } finally {
      clearAuth();
      setUser(null);
    }
  }, []);

  const refreshUser = useCallback(async (): Promise<void> => {
    if (!loadAuth()) return;
    try {
      const me = await getMe();
      saveUser(me);
      setUser(me);
    } catch {
      // getMe() already clears the session if a refresh fails; reflect that.
      setUser(loadAuth()?.user ?? null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      loginWithPhone,
      logout,
      refreshUser,
    }),
    [user, loginWithPhone, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
