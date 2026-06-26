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

import { getFirebaseAuth } from './firebase';

/** A live phone-sign-in attempt: hold it between "send code" and "verify". */
export interface PhoneSignInSession {
  /** Confirm the SMS OTP and return a real Firebase ID token. */
  confirm: (otp: string) => Promise<string>;
}

/**
 * Map the common Firebase auth error codes to friendly, user-facing messages.
 * Anything else falls through to a generic message; the raw code is preserved
 * on the thrown Error for debugging (never the token).
 */
function friendlyFirebaseError(err: unknown): Error {
  const code =
    typeof err === 'object' && err !== null && 'code' in err
      ? String((err as { code: unknown }).code)
      : '';
  switch (code) {
    case 'auth/invalid-phone-number':
      return new Error('That mobile number looks invalid. Please check it.');
    case 'auth/invalid-verification-code':
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
      return new Error('Could not send the code. Please try again.');
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

  try {
    const verifier = new RecaptchaVerifier(auth, containerId, {
      size: 'invisible',
    });
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
          throw friendlyFirebaseError(err);
        }
      },
    };
  } catch (err) {
    throw friendlyFirebaseError(err);
  }
}
