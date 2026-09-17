'use client';

import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { useLocationGate } from '@/app/components/LocationGate';
import {
  MIN_VISITS_BEFORE_PROMPT,
  canPromptInstall,
  countVisit,
  isInstallDismissed,
  isInstalled,
  isIosSafari,
  promptInstall,
  rememberInstallDismissed,
  subscribeToInstallState,
} from '@/app/lib/install';

/**
 * The storefront's own "add Town Basket to your home screen" ask
 * (ARCHITECTURE.md §4.1).
 *
 * <p>Two shapes, because the two platforms work differently: on Chromium we
 * replay the install prompt the browser handed us, so it is one tap; on iOS
 * Safari no such API exists and the best we can do is show the Share-sheet
 * steps. Every other browser gets nothing — a banner that cannot lead to an
 * install is just something in the way of the groceries.
 *
 * <p>Renders nothing until an effect has decided, which also keeps it out of
 * the server-rendered HTML: installability is a property of the browser, so
 * there is no correct thing to render before hydration.
 *
 * <p>Asks once. Declining is remembered permanently (see
 * {@link rememberInstallDismissed}).
 */
export default function InstallPrompt() {
  const t = useTranslations('install');
  const { blocking } = useLocationGate();
  const [mode, setMode] = useState<'hidden' | 'prompt' | 'ios'>('hidden');
  const [showSteps, setShowSteps] = useState(false);

  useEffect(() => {
    // Already installed, or already told us no — nothing to ask.
    if (isInstalled() || isInstallDismissed()) return;
    if (countVisit() < MIN_VISITS_BEFORE_PROMPT) return;

    const decide = () => {
      if (isInstalled()) {
        setMode('hidden');
        return;
      }
      if (canPromptInstall()) {
        setMode('prompt');
        return;
      }
      setMode(isIosSafari() ? 'ios' : 'hidden');
    };

    decide();
    // Chrome may fire `beforeinstallprompt` after this effect has run, so keep
    // listening — that is what turns an iOS-style banner into a one-tap one,
    // and what clears it when an install completes elsewhere.
    return subscribeToInstallState(decide);
  }, []);

  const dismiss = useCallback(() => {
    rememberInstallDismissed();
    setMode('hidden');
  }, []);

  const install = useCallback(async () => {
    const outcome = await promptInstall();
    // 'accepted' hides via the `appinstalled` event too, but not every browser
    // fires it reliably; hiding here means the banner never outlives the tap.
    // 'dismissed' is the customer saying no to the browser's own dialog, which
    // is as clear an answer as tapping our dismiss button.
    if (outcome === 'dismissed') rememberInstallDismissed();
    setMode('hidden');
  }, []);

  // "Where do you live?" has to be answered before "would you like to install
  // this?" is even a sensible question, and the gate's overlay is translucent
  // enough to show this banner through it. Wait for the shell to be the
  // customer's own again — the mode is kept, so it appears once the gate clears.
  if (blocking || mode === 'hidden') return null;

  return (
    <div className="install-prompt" role="region" aria-label={t('title')}>
      <span className="install-prompt-icon" aria-hidden>
        📲
      </span>
      <div className="install-prompt-body">
        <p className="install-prompt-text">
          <strong>{t('title')}</strong> {t('body')}
        </p>

        {mode === 'ios' && showSteps && (
          <ol className="install-steps">
            <li>{t('iosStepShare')}</li>
            <li>{t('iosStepAdd')}</li>
            <li>{t('iosStepConfirm')}</li>
          </ol>
        )}
      </div>

      <div className="install-prompt-actions">
        {mode === 'prompt' ? (
          <button type="button" className="btn install-prompt-cta" onClick={install}>
            {t('install')}
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-outline install-prompt-cta"
            aria-expanded={showSteps}
            onClick={() => setShowSteps((open) => !open)}
          >
            {showSteps ? t('hideHow') : t('showHow')}
          </button>
        )}
        <button
          type="button"
          className="install-prompt-dismiss"
          onClick={dismiss}
          aria-label={t('dismiss')}
        >
          ✕
        </button>
      </div>
    </div>
  );
}
