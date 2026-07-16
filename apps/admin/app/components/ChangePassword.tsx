'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, changePassword } from '@/app/lib/api';

const MIN_LEN = 8;

/**
 * Compact "Change password" control for staff/admin (Change 3 consumer).
 * Renders as a single header button (sits next to Logout); clicking it opens a
 * small modal with current / new / confirm fields and a client check
 * (new === confirm, length >= 8) before POST /me/password via the authed
 * apiMutate (Bearer attached automatically). On success the session is kept
 * (tokens stay valid); errors are mapped to friendly copy:
 *   422 → "Current password is incorrect" / "not available for this account"
 *   400 → "New password must be at least 8 characters"
 */
export default function ChangePassword() {
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const modalRef = useRef<HTMLDivElement>(null);

  function reset() {
    setCurrent('');
    setNext('');
    setConfirm('');
    setError(null);
  }

  function toggle() {
    setOpen((o) => {
      if (o) reset();
      setSuccess(false);
      return !o;
    });
  }

  const dirty = current.length > 0 || next.length > 0 || confirm.length > 0;

  /** Close only when nothing typed would be lost (backdrop / Escape path). */
  function requestClose() {
    if (dirty && !window.confirm('Discard the passwords you typed?')) return;
    toggle();
  }

  // While open: close on Escape, keep Tab cycling inside the dialog, and
  // restore focus to the trigger button when the dialog closes.
  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        requestClose();
        return;
      }
      if (e.key === 'Tab' && modalRef.current) {
        const focusables = modalRef.current.querySelectorAll<HTMLElement>(
          'button, input, [href], select, textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      triggerRef.current?.focus();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, dirty]);

  const mismatch = confirm.length > 0 && next !== confirm;
  const tooShort = next.length > 0 && next.length < MIN_LEN;
  const canSubmit =
    !busy &&
    current.length > 0 &&
    next.length >= MIN_LEN &&
    next === confirm;

  function mapError(err: unknown): string {
    if (err instanceof ApiError) {
      if (err.status === 422) {
        // Distinguish "no password on this account" from "wrong current".
        return /not available/i.test(err.message)
          ? 'Password change is not available for this account.'
          : 'Current password is incorrect.';
      }
      if (err.status === 400) {
        return 'New password must be at least 8 characters.';
      }
      if (err.status === 0) {
        return 'Could not reach the server. Check your connection.';
      }
    }
    return 'Could not change your password. Please try again.';
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setError(null);
    setSuccess(false);
    try {
      await changePassword(current, next);
      setSuccess(true);
      reset();
      setOpen(false);
    } catch (err) {
      setError(mapError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button
        type="button"
        ref={triggerRef}
        className="btn btn-ghost admin-logout"
        onClick={toggle}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        Change password
      </button>
      {success && !open && <span className="pw-ok-note">Password updated</span>}

      {open && (
        <div
          className="pw-modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="Change password"
          onClick={requestClose}
        >
          <div className="pw-modal" ref={modalRef} onClick={(e) => e.stopPropagation()}>
            <div className="pw-modal-head">
              <h2 className="account-panel-title">Change password</h2>
              <button
                type="button"
                className="pw-modal-close"
                onClick={requestClose}
                aria-label="Close"
              >
                ×
              </button>
            </div>

            <form className="account-form" onSubmit={onSubmit}>
              <label className="login-field" htmlFor="pw-current">
                Current password
                <input
                  id="pw-current"
                  type="password"
                  autoComplete="current-password"
                  value={current}
                  onChange={(e) => setCurrent(e.target.value)}
                  required
                  autoFocus
                />
              </label>

              <label className="login-field" htmlFor="pw-new">
                New password
                <input
                  id="pw-new"
                  type="password"
                  autoComplete="new-password"
                  value={next}
                  onChange={(e) => setNext(e.target.value)}
                  required
                  minLength={MIN_LEN}
                  aria-invalid={tooShort}
                />
                {tooShort && (
                  <span className="field-hint">
                    Must be at least {MIN_LEN} characters.
                  </span>
                )}
              </label>

              <label className="login-field" htmlFor="pw-confirm">
                Confirm new password
                <input
                  id="pw-confirm"
                  type="password"
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  required
                  aria-invalid={mismatch}
                />
                {mismatch && (
                  <span className="field-hint">Passwords do not match.</span>
                )}
              </label>

              {error && <p className="account-banner err">{error}</p>}

              <button type="submit" className="btn" disabled={!canSubmit}>
                {busy ? 'Saving…' : 'Save new password'}
              </button>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
