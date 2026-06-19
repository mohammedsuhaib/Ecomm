'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
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
import { isFirebaseConfigured } from '@/app/lib/firebase';
import {
  startPhoneSignIn,
  type PhoneSignInSession,
} from '@/app/lib/firebasePhoneAuth';
import type { UserDto } from '@/app/lib/types';

// Auth state shared across the storefront shell: the header link, the login
// page (which also runs cart-merge), the account area, and checkout prefill all
// read through this context. Tokens themselves are kept in localStorage (see
// app/lib/auth.ts for the XSS trade-off note); this context exposes only the
// user + actions, never the raw tokens.
interface AuthContextValue {
  user: UserDto | null;
  isAuthenticated: boolean;
  /**
   * True when the REAL Firebase phone-OTP flow is active (NEXT_PUBLIC_FIREBASE_*
   * configured). When false, the dev/fake verifier fallback is used and any
   * 6-digit code is accepted locally. Lets the login UI tailor its copy.
   */
  firebaseEnabled: boolean;
  /**
   * Step 1 — "send code". In real mode this actually sends an SMS OTP via
   * Firebase (an invisible reCAPTCHA is mounted on `recaptchaContainerId`) and
   * stashes the confirmation session for `loginWithPhone`. In dev mode this is
   * a no-op (no SMS is sent). Throws a friendly Error if the send fails.
   */
  startPhoneLogin: (phone: string, recaptchaContainerId: string) => Promise<void>;
  /**
   * Step 2 — verify the code, store tokens+user, return the signed-in user. In
   * real mode this confirms the SMS OTP against the session from
   * `startPhoneLogin` and POSTs the genuine Firebase ID token; in dev mode it
   * sends `dev:<phone>` (the OTP is cosmetic locally).
   */
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

  // Whether the real Firebase flow is configured. Computed once; the string
  // check is SSR/build-safe and does not import the Firebase SDK.
  const firebaseEnabled = isFirebaseConfigured();

  // Holds the in-flight Firebase confirmation session between "send code"
  // (startPhoneLogin) and "verify" (loginWithPhone). Unused in dev mode.
  const phoneSessionRef = useRef<PhoneSignInSession | null>(null);

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

  // ---- Step 1: send code -------------------------------------------------
  // Real mode (NEXT_PUBLIC_FIREBASE_* set): actually send the SMS OTP via the
  // Firebase Web SDK (invisible reCAPTCHA mounted on the given container) and
  // stash the confirmation session for loginWithPhone. The `firebase` SDK is
  // dynamically imported, in the browser, only here — never at SSR/build.
  // Dev mode: no SMS is sent; the OTP step is cosmetic (any 6-digit code).
  const startPhoneLogin = useCallback(
    async (phone: string, recaptchaContainerId: string): Promise<void> => {
      if (!firebaseEnabled) return; // dev: nothing to send
      phoneSessionRef.current = await startPhoneSignIn(
        phone,
        recaptchaContainerId,
      );
    },
    [firebaseEnabled],
  );

  // ---- Step 2: verify ----------------------------------------------------
  // Real mode: confirm the SMS OTP against the session from startPhoneLogin,
  // mint a genuine Firebase ID token, and POST it to /auth/phone/verify.
  // Dev mode: the backend's dev/fake verifier accepts `dev:<phone>` (the OTP is
  // cosmetic and is not sent). Tokens are never logged.
  const loginWithPhone = useCallback(
    async (phone: string, otp: string): Promise<UserDto> => {
      let firebaseIdToken: string;
      if (firebaseEnabled) {
        const session = phoneSessionRef.current;
        if (!session) {
          throw new Error('Please request a code before verifying.');
        }
        firebaseIdToken = await session.confirm(otp); // real Firebase ID token
      } else {
        void otp; // cosmetic in dev; consumed by the real Firebase SDK in prod
        firebaseIdToken = `dev:${phone}`;
      }

      const res = await phoneVerify(firebaseIdToken);
      phoneSessionRef.current = null; // consume the session on success
      saveAuth({
        accessToken: res.accessToken,
        refreshToken: res.refreshToken,
        user: res.user,
      });
      setUser(res.user);
      return res.user;
    },
    [firebaseEnabled],
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
      firebaseEnabled,
      startPhoneLogin,
      loginWithPhone,
      logout,
      refreshUser,
    }),
    [user, firebaseEnabled, startPhoneLogin, loginWithPhone, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
