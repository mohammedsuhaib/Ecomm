// Real Firebase Web SDK phone-OTP flow (browser only).
//
// Used by AuthProvider/login ONLY when `isFirebaseConfigured()` is true. Two
// steps:
//   1) startPhoneSignIn(phone10, containerId) — build an INVISIBLE
//      RecaptchaVerifier on the given container, then signInWithPhoneNumber for
//      +91<phone10>. Returns a session handle holding the ConfirmationResult.
//   2) session.confirm(otp) — confirm the SMS code, then read a REAL Firebase
//      ID token (user.getIdToken()) to POST to /auth/phone/verify.
//
// All `firebase/*` imports are dynamic so the SDK never lands in the SSR/build
// path (mirrors app/lib/firebase.ts). We never log the token.

import type { RecaptchaVerifier as RecaptchaVerifierType } from 'firebase/auth';
import { getFirebaseAuth } from './firebase';

// The invisible reCAPTCHA can only be rendered ONCE per element, and clearing
// an invisible verifier does NOT detach its DOM — so "Resend code" destroys
// the previous verifier instance here AND renders the next attempt on a fresh
// child of the container (see startPhoneSignIn). Module scope is fine: one
// login page, one attempt at a time.
let activeVerifier: RecaptchaVerifierType | null = null;

function clearActiveVerifier(): void {
  try {
    activeVerifier?.clear();
  } catch {
    /* already gone */
  }
  activeVerifier = null;
}

/** A live phone-sign-in attempt: hold it between "send code" and "verify". */
export interface PhoneSignInSession {
  /** Confirm the SMS OTP and return a real Firebase ID token. */
  confirm: (otp: string) => Promise<string>;
}

/**
 * Map the common Firebase auth error codes to friendly, user-facing messages.
 * Anything else falls through to a generic message for the stage that failed —
 * a failed CONFIRM must never claim the code "could not be sent", or a wrong
 * OTP reads as a resend problem instead of an invalid code.
 */
function friendlyFirebaseError(err: unknown, stage: 'send' | 'confirm'): Error {
  const code =
    typeof err === 'object' && err !== null && 'code' in err
      ? String((err as { code: unknown }).code)
      : '';
  switch (code) {
    case 'auth/invalid-phone-number':
      return new Error('That mobile number looks invalid. Please check it.');
    case 'auth/invalid-verification-code':
    case 'auth/missing-verification-code':
      return new Error('That code is incorrect. Please re-enter it.');
    case 'auth/code-expired':
      return new Error('That code has expired. Please request a new one.');
    case 'auth/too-many-requests':
      return new Error('Too many attempts. Please wait a little and try again.');
    case 'auth/quota-exceeded':
      return new Error('SMS limit reached. Please try again later.');
    case 'auth/captcha-check-failed':
      return new Error('Verification failed. Please reload and try again.');
    default:
      return stage === 'confirm'
        ? new Error('That code could not be verified. Please re-enter it.')
        : new Error('Could not send the code. Please try again.');
  }
}

/**
 * Step 1 — send the SMS code. Creates an invisible reCAPTCHA bound to
 * `containerId` and starts the phone sign-in for the 10-digit Indian number.
 * Returns a session whose `.confirm(otp)` completes step 2.
 */
export async function startPhoneSignIn(
  phone10: string,
  containerId: string,
): Promise<PhoneSignInSession> {
  const { RecaptchaVerifier, signInWithPhoneNumber } = await import(
    'firebase/auth'
  );
  const auth = await getFirebaseAuth();

  clearActiveVerifier();

  // grecaptcha refuses to render a second widget into an element it has
  // already used, and for an INVISIBLE verifier `clear()` does not detach the
  // widget's DOM (the SDK only empties the container for visible sizes). So a
  // resend on the same container id throws "already been rendered". Mount
  // every attempt on a fresh child element instead of the container itself.
  const container = document.getElementById(containerId);
  if (!container) {
    throw new Error('Could not send the code. Please reload and try again.');
  }
  container.replaceChildren();
  const mount = document.createElement('div');
  container.appendChild(mount);

  try {
    const verifier = new RecaptchaVerifier(auth, mount, {
      size: 'invisible',
    });
    activeVerifier = verifier;
    const confirmationResult = await signInWithPhoneNumber(
      auth,
      `+91${phone10}`,
      verifier,
    );

    return {
      confirm: async (otp: string): Promise<string> => {
        try {
          const cred = await confirmationResult.confirm(otp);
          return await cred.user.getIdToken();
        } catch (err) {
          throw friendlyFirebaseError(err, 'confirm');
        }
      },
    };
  } catch (err) {
    // A failed send leaves the widget in an unknown state; drop it so the
    // next attempt (or resend) starts clean instead of hitting "already rendered".
    clearActiveVerifier();
    throw friendlyFirebaseError(err, 'send');
  }
}
