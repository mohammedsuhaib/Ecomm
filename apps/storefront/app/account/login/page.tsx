'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { ApiError, getMyCart, mergeCart } from '@/app/lib/api';
import { loadCartId, saveCartId } from '@/app/lib/cart';
import { useAuth } from '@/app/components/AuthProvider';
import { useCartActions } from '@/app/components/CartProvider';
import { PhoneAuthError } from '@/app/lib/firebasePhoneAuth';

type Step = 'phone' | 'code';

export default function LoginPage() {
  const t = useTranslations('login');
  const tc = useTranslations('common');
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get('next') || '/account';

  const { loginWithPhone, startPhoneLogin, firebaseEnabled } = useAuth();
  const { refresh } = useCartActions();

  // Invisible reCAPTCHA mount point for the real Firebase phone flow. Firebase
  // binds the widget to this element id during "send code"; it is harmless
  // (empty) in dev mode where no SMS is sent.
  const RECAPTCHA_CONTAINER_ID = 'tb-recaptcha-container';

  const [step, setStep] = useState<Step>('phone');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Resend cooldown, in seconds. Starts when a code goes out; the button is
  // dead until it hits 0 so a nervous double-tap cannot burn SMS quota or the
  // per-IP auth rate limit. 30 s is about how long an Indian SMS takes to
  // arrive when it is going to arrive at all.
  const [resendIn, setResendIn] = useState(0);
  const [resent, setResent] = useState(false);
  const RESEND_COOLDOWN_S = 30;

  useEffect(() => {
    if (resendIn <= 0) return;
    const id = setTimeout(() => setResendIn((n) => n - 1), 1000);
    return () => clearTimeout(id);
  }, [resendIn]);

  const phoneValid = useMemo(() => /^[0-9]{10}$/.test(phone.trim()), [phone]);
  const codeValid = useMemo(() => /^[0-9]{6}$/.test(code.trim()), [code]);

  async function sendCode(e: React.FormEvent) {
    e.preventDefault();
    if (!phoneValid || busy) {
      if (!phoneValid) setError(t('enterValidMobile'));
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
      setResendIn(RESEND_COOLDOWN_S);
      setResent(false);
    } catch (err) {
      setError(
        err instanceof PhoneAuthError ? t(err.messageKey) : t('couldNotSend'),
      );
    } finally {
      setBusy(false);
    }
  }

  // Same send again, from the code step. In real mode this re-runs the SMS
  // (the previous reCAPTCHA widget is torn down first); in dev mode it is a
  // no-op that just resets the cooldown — the UI must behave identically.
  async function resend() {
    if (busy || resendIn > 0) return;
    setBusy(true);
    setError(null);
    setResent(false);
    try {
      await startPhoneLogin(phone.trim(), RECAPTCHA_CONTAINER_ID);
      setCode('');
      setResent(true);
      setResendIn(RESEND_COOLDOWN_S);
    } catch (err) {
      setError(
        err instanceof PhoneAuthError ? t(err.messageKey) : t('couldNotSend'),
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
    // Drop a stale "A new code has been sent." notice: if this verify fails,
    // the invalid-code error must not appear alongside a resend success note.
    setResent(false);
    try {
      await loginWithPhone(phone.trim(), code.trim());

      // Cart handover: with a local (guest) cart, fold it into the user's
      // active cart and store the returned cartId (it may differ). With NO
      // local cart — a new device, or after logout cleared it — fetch the
      // user's open cart from the server, so the basket follows the account
      // rather than the browser. Both are best-effort: login must not fail
      // over a cart.
      const guestCartId = loadCartId();
      if (guestCartId) {
        try {
          const merged = await mergeCart(guestCartId);
          saveCartId(merged.cartId);
        } catch {
          /* non-fatal: keep the guest cart as-is if merge fails */
        }
      } else {
        try {
          const mine = await getMyCart();
          if (mine && !mine.checkedOut) saveCartId(mine.cartId);
        } catch {
          /* non-fatal: start with an empty cart if the lookup fails */
        }
      }
      await refresh();

      router.replace(next);
    } catch (err) {
      if (err instanceof PhoneAuthError) {
        // Wrong or expired code from the real Firebase flow — the one error a
        // customer sees most, so it gets its own translated copy rather than
        // the SDK's English message.
        setError(t(err.messageKey));
      } else if (err instanceof ApiError && err.status === 401) {
        setError(t('couldNotVerify'));
      } else if (err instanceof ApiError && err.status === 429) {
        // The auth endpoints are rate-limited per client IP, and an IP is not a
        // person: a shop's WiFi or a carrier's CGNAT pool can put a whole group
        // of customers on one address. So this is "the queue ahead of you", not
        // "you did something wrong" — and waiting, not retrying, is the fix.
        setError(t('tooManySignInAttempts'));
      } else {
        setError(t('signInError'));
      }
      setBusy(false);
    }
  }

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">{tc('home')}</Link> / <span>{tc('login')}</span>
      </nav>

      <h1 className="section-title" style={{ marginTop: 0 }}>
        {t('signIn')}
      </h1>

      {error && <p className="notice error">{error}</p>}

      {/* Invisible reCAPTCHA mount for the real Firebase phone flow. Lives
          outside the step switch so it exists for BOTH the first send and a
          resend from the code step; harmless and empty in dev mode. */}
      <div id={RECAPTCHA_CONTAINER_ID} />

      {step === 'phone' ? (
        <form className="auth-form" onSubmit={sendCode}>
          <div className="field">
            <label htmlFor="phone">{t('mobileNumber')}</label>
            <input
              id="phone"
              inputMode="numeric"
              autoComplete="tel"
              placeholder={t('mobilePlaceholder')}
              value={phone}
              maxLength={10}
              onChange={(e) =>
                setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))
              }
              autoFocus
            />
            {phone && !phoneValid && (
              <span className="add-error">{t('phoneError')}</span>
            )}
          </div>
          <button
            type="submit"
            className="btn btn-block"
            disabled={!phoneValid || busy}
          >
            {busy ? t('sending') : t('sendCode')}
          </button>
          {firebaseEnabled ? (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              {t.rich('smsHint', {
                number: `+91 ${phone || t('yourNumber')}`,
                b: (chunks) => <strong>{chunks}</strong>,
              })}
            </p>
          ) : (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              {t('devHintPhone')}
            </p>
          )}
        </form>
      ) : (
        <form className="auth-form" onSubmit={verify}>
          <p className="muted">
            {t.rich('enterCodeSent', {
              number: `+91 ${phone}`,
              b: (chunks) => <strong>{chunks}</strong>,
            })}
          </p>
          <div className="field">
            <label htmlFor="code">{t('verificationCode')}</label>
            <input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder={t('codePlaceholder')}
              value={code}
              maxLength={6}
              onChange={(e) =>
                setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
              }
              autoFocus
            />
            {code && !codeValid && (
              <span className="add-error">{t('codeError')}</span>
            )}
          </div>
          <button
            type="submit"
            className="btn btn-block"
            disabled={!codeValid || busy}
          >
            {busy ? t('verifying') : t('verifyContinue')}
          </button>
          {resent && (
            <p className="notice" role="status">{t('codeResent')}</p>
          )}
          <button
            type="button"
            className="btn btn-outline btn-block"
            disabled={busy || resendIn > 0}
            aria-live="polite"
            onClick={resend}
          >
            {resendIn > 0 ? t('resendIn', { seconds: resendIn }) : t('resendCode')}
          </button>
          <button
            type="button"
            className="btn btn-outline btn-block"
            disabled={busy}
            onClick={() => {
              setStep('phone');
              setCode('');
              setError(null);
              setResent(false);
            }}
          >
            {t('changeNumber')}
          </button>
          {firebaseEnabled ? (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              {t('smsHintVerify')}
            </p>
          ) : (
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              {t('devHintCode')}
            </p>
          )}
        </form>
      )}
    </>
  );
}
