'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ApiError, mergeCart } from '@/app/lib/api';
import { loadCartId, saveCartId } from '@/app/lib/cart';
import { useAuth } from '@/app/components/AuthProvider';
import { useCart } from '@/app/components/CartProvider';

type Step = 'phone' | 'code';

export default function LoginPage() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get('next') || '/account';

  const { loginWithPhone, startPhoneLogin, firebaseEnabled } = useAuth();
  const { refresh } = useCart();

  // Invisible reCAPTCHA mount point for the real Firebase phone flow. Firebase
  // binds the widget to this element id during "send code"; it is harmless
  // (empty) in dev mode where no SMS is sent.
  const RECAPTCHA_CONTAINER_ID = 'tb-recaptcha-container';

  const [step, setStep] = useState<Step>('phone');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const phoneValid = useMemo(() => /^[0-9]{10}$/.test(phone.trim()), [phone]);
  const codeValid = useMemo(() => /^[0-9]{6}$/.test(code.trim()), [code]);

  async function sendCode(e: React.FormEvent) {
    e.preventDefault();
    if (!phoneValid || busy) {
      if (!phoneValid) setError('Enter a valid 10-digit mobile number.');
      return;
    }
    setError(null);
    // Real mode: actually send the SMS OTP via Firebase (this also runs the
    // invisible reCAPTCHA). Dev mode: no-op — the OTP step is cosmetic locally
    // and any 6-digit code works.
    setBusy(true);
    try {
      await startPhoneLogin(phone.trim(), RECAPTCHA_CONTAINER_ID);
      setStep('code');
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not send the code. Please try again.',
      );
    } finally {
      setBusy(false);
    }
  }

  async function verify(e: React.FormEvent) {
    e.preventDefault();
    if (!codeValid || busy) return;
    setBusy(true);
    setError(null);
    try {
      await loginWithPhone(phone.trim(), code.trim());

      // Cart merge: fold any guest cart into the user's active cart, then store
      // the returned cartId (it may differ) and refresh the cart context.
      const guestCartId = loadCartId();
      if (guestCartId) {
        try {
          const merged = await mergeCart(guestCartId);
          saveCartId(merged.cartId);
        } catch {
          /* non-fatal: keep the guest cart as-is if merge fails */
        }
      }
      await refresh();

      router.replace(next);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('We could not verify that number. Please try again.');
      } else if (err instanceof ApiError) {
        setError('Something went wrong signing you in. Please try again.');
      } else if (err instanceof Error) {
        // Friendly Firebase error (e.g. wrong/expired code) from the real flow.
        setError(err.message);
      } else {
        setError('Something went wrong signing you in. Please try again.');
      }
      setBusy(false);
    }
  }

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">Home</Link> / <span>Login</span>
      </nav>

      <h1 className="section-title" style={{ marginTop: 0 }}>
        Sign in
      </h1>

      {error && <p className="notice error">{error}</p>}

      {step === 'phone' ? (
        <form className="auth-form" onSubmit={sendCode}>
          <div className="field">
            <label htmlFor="phone">Mobile number</label>
            <input
              id="phone"
              inputMode="numeric"
              autoComplete="tel"
              placeholder="10-digit mobile"
              value={phone}
              maxLength={10}
              onChange={(e) =>
                setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))
              }
              autoFocus
            />
            {phone && !phoneValid && (
              <span className="add-error">Enter a 10-digit phone number.</span>
            )}
          </div>
          <button
            type="submit"
            className="btn btn-block"
            disabled={!phoneValid || busy}
          >
            {busy ? 'Sending…' : 'Send code'}
          </button>
          {firebaseEnabled ? (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              We&apos;ll text a 6-digit code to <strong>+91 {phone || 'your number'}</strong>.
            </p>
          ) : (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              Local dev: no SMS is sent — enter any 6-digit code on the next step.
            </p>
          )}
          {/* Invisible reCAPTCHA mount for the real Firebase phone flow. */}
          <div id={RECAPTCHA_CONTAINER_ID} />
        </form>
      ) : (
        <form className="auth-form" onSubmit={verify}>
          <p className="muted">
            Enter the 6-digit code sent to <strong>+91 {phone}</strong>.
          </p>
          <div className="field">
            <label htmlFor="code">Verification code</label>
            <input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="6-digit code"
              value={code}
              maxLength={6}
              onChange={(e) =>
                setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
              }
              autoFocus
            />
            {code && !codeValid && (
              <span className="add-error">Enter the 6-digit code.</span>
            )}
          </div>
          <button
            type="submit"
            className="btn btn-block"
            disabled={!codeValid || busy}
          >
            {busy ? 'Verifying…' : 'Verify & continue'}
          </button>
          <button
            type="button"
            className="btn btn-outline btn-block"
            disabled={busy}
            onClick={() => {
              setStep('phone');
              setCode('');
              setError(null);
            }}
          >
            Change number
          </button>
          {firebaseEnabled ? (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              Enter the code from the SMS we just sent.
            </p>
          ) : (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              Local dev: any 6-digit code is accepted.
            </p>
          )}
        </form>
      )}
    </>
  );
}
