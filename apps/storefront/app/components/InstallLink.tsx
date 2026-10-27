'use client';

import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import {
  canPromptInstall,
  isInstalled,
  isIosSafari,
  promptInstall,
  subscribeToInstallState,
} from '@/app/lib/install';

/**
 * A standing "Install app" entry in the footer.
 *
 * <p>The banner ({@link InstallPrompt}) asks once, on a second visit, and a
 * dismissal is permanent — so a customer who tapped "No thanks", or simply never
 * saw it, had no way back. This is that way back: always there while the app is
 * installable, never nagging. It ignores the dismissal and the visit count on
 * purpose; the customer choosing to tap it is the strongest signal there is.
 *
 * <p>Renders nothing where there is nothing to offer: already installed, or a
 * browser that neither handed us an install prompt nor is iOS Safari.
 */
export default function InstallLink() {
  const t = useTranslations('install');
  const [mode, setMode] = useState<'hidden' | 'prompt' | 'ios'>('hidden');
  const [showSteps, setShowSteps] = useState(false);

  useEffect(() => {
    const decide = () => {
      if (isInstalled()) setMode('hidden');
      else if (canPromptInstall()) setMode('prompt');
      else setMode(isIosSafari() ? 'ios' : 'hidden');
    };
    decide();
    return subscribeToInstallState(decide);
  }, []);

  if (mode === 'hidden') return null;

  return (
    <div className="footer-install">
      <button
        type="button"
        className="footer-install-btn"
        aria-expanded={mode === 'ios' ? showSteps : undefined}
        onClick={() => {
          if (mode === 'prompt') void promptInstall();
          else setShowSteps((open) => !open);
        }}
      >
        📲 {t('footerLink')}
      </button>
      {mode === 'ios' && showSteps && (
        <ol className="install-steps">
          <li>{t('iosStepShare')}</li>
          <li>{t('iosStepAdd')}</li>
          <li>{t('iosStepConfirm')}</li>
        </ol>
      )}
    </div>
  );
}
