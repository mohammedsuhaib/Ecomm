'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ApiError, mergeCart } from '@/app/lib/api';
import { loadCartId, saveCartId } from '@/app/lib/cart';
import { useAuth } from '@/app/components/AuthProvider';
import { useCart } from '@/app/components/CartProvider';

type Step = 'phone' | 'code';

export default function LoginPage() {
  const t = useTranslations('login');
  const tc = useTranslations('common');
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
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : t('couldNotSend'),
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
        setError(t('couldNotVerify'));
      } else if (err instanceof ApiError) {
        setError(t('signInError'));
      } else if (err instanceof Error) {
        // Friendly Firebase error (e.g. wrong/expired code) from the real flow.
        setError(err.message);
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
          {/* Invisible reCAPTCHA mount for the real Firebase phone flow. */}
          <div id={RECAPTCHA_CONTAINER_ID} />
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
