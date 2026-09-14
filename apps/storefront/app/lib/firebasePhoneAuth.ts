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
 * What went wrong, as a key in the storefront's `login` message namespace.
 *
 * <p>This module classifies; it does not write copy. It used to build English
 * `Error` messages that the login page rendered verbatim, which handed a
 * Kannada customer an English sentence at the one step they were stuck on —
 * every other string on that page goes through next-intl. Returning a key
 * keeps the wording in `messages/*.json` for both languages.
 */
export type PhoneAuthErrorKey =
  | 'codeIncorrect'
  | 'codeMissing'
  | 'codeExpired'
  | 'invalidPhone'
  | 'tooManyAttempts'
  | 'smsQuotaExceeded'
  | 'captchaFailed'
  | 'codeNotVerified'
  | 'requestCodeFirst'
  | 'couldNotSend';

/**
 * A phone-sign-in failure the login UI can phrase for the customer.
 *
 * <p>`messageKey` is the customer-facing copy; `message` carries the
 * underlying Firebase code for logs and is never displayed — showing a raw
 * SDK message to a shopper is both unreadable and a small information leak.
 */
export class PhoneAuthError extends Error {
  readonly messageKey: PhoneAuthErrorKey;

  constructor(messageKey: PhoneAuthErrorKey, detail: string) {
    super(`${messageKey} (${detail})`);
    this.name = 'PhoneAuthError';
    this.messageKey = messageKey;
  }
}

/**
 * Classify a Firebase auth error into one of the keys above.
 *
 * <p>Anything unrecognised falls back per stage: a failed CONFIRM must never
 * claim the code "could not be sent", or a wrong OTP reads as a resend problem
 * instead of a bad code.
 */
function classifyFirebaseError(
  err: unknown,
  stage: 'send' | 'confirm',
): PhoneAuthError {
  const code =
    typeof err === 'object' && err !== null && 'code' in err
      ? String((err as { code: unknown }).code)
      : '';
  switch (code) {
    case 'auth/invalid-phone-number':
      return new PhoneAuthError('invalidPhone', code);
    case 'auth/invalid-verification-code':
      return new PhoneAuthError('codeIncorrect', code);
    case 'auth/missing-verification-code':
      return new PhoneAuthError('codeMissing', code);
    // The SMS code itself timed out, or the whole verification session did —
    // either way the only way forward is a fresh code, not the same digits.
    case 'auth/code-expired':
    case 'auth/session-expired':
      return new PhoneAuthError('codeExpired', code);
    case 'auth/too-many-requests':
      return new PhoneAuthError('tooManyAttempts', code);
    case 'auth/quota-exceeded':
      return new PhoneAuthError('smsQuotaExceeded', code);
    case 'auth/captcha-check-failed':
      return new PhoneAuthError('captchaFailed', code);
    default:
      return stage === 'confirm'
        ? new PhoneAuthError('codeNotVerified', code || 'unknown')
        : new PhoneAuthError('couldNotSend', code || 'unknown');
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
    throw new PhoneAuthError('couldNotSend', 'recaptcha-container-missing');
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
          throw classifyFirebaseError(err, 'confirm');
        }
      },
    };
  } catch (err) {
    // A failed send leaves the widget in an unknown state; drop it so the
    // next attempt (or resend) starts clean instead of hitting "already rendered".
    clearActiveVerifier();
    throw classifyFirebaseError(err, 'send');
  }
}
