// Browser-only Firebase bootstrap for the customer phone-OTP login.
//
// CRITICAL: nothing here touches the Firebase SDK at module load. CI builds the
// storefront with NO `NEXT_PUBLIC_FIREBASE_*` env set, so:
//   - `isFirebaseConfigured()` returns false → the login flow falls back to the
//     backend dev/fake verifier (`dev:<phone>`), and
//   - `getFirebaseAuth()` is only ever called from the real flow (which is
//     gated behind `isFirebaseConfigured()`), and even then it dynamically
//     imports `firebase/*` lazily, in the browser, exactly once.
//
// This keeps `firebase` out of the SSR/prerender path: it is never imported at
// the top level, never initialised during `next build`, and never on the
// server. We deliberately do NOT use getAnalytics.
//
// The six values below are PUBLIC (Firebase Web config is safe to expose in the
// browser); Next inlines `NEXT_PUBLIC_*` at build time (see Dockerfile ARGs).

import type { Auth } from 'firebase/auth';

// Read once at module eval. These are plain strings (or undefined) — reading
// `process.env.NEXT_PUBLIC_*` does NOT import the SDK, so it is SSR/build safe.
const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
} as const;

/**
 * True iff the storefront has enough Firebase config to run the REAL phone-OTP
 * flow. We require at least apiKey + projectId; when false, the login page uses
 * the dev/fake verifier fallback. Pure string check — no SDK import.
 */
export function isFirebaseConfigured(): boolean {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.projectId);
}

// Memoised Auth instance (browser only). Lives at module scope but is only ever
// populated inside getFirebaseAuth() under a `typeof window` guard.
let authPromise: Promise<Auth> | null = null;

/**
 * Lazily initialise Firebase App + Auth exactly once, in the browser. Throws if
 * called on the server or when Firebase is not configured (callers must gate on
 * `isFirebaseConfigured()` first). Uses a dynamic import so the SDK is only
 * pulled into the client bundle/runtime when the real flow actually runs.
 */
export function getFirebaseAuth(): Promise<Auth> {
  if (typeof window === 'undefined') {
    return Promise.reject(
      new Error('getFirebaseAuth() must only be called in the browser'),
    );
  }
  if (!isFirebaseConfigured()) {
    return Promise.reject(new Error('Firebase is not configured'));
  }
  if (!authPromise) {
    authPromise = (async () => {
      const { initializeApp, getApps, getApp } = await import('firebase/app');
      const { getAuth } = await import('firebase/auth');
      const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
      return getAuth(app);
    })();
  }
  return authPromise;
}
