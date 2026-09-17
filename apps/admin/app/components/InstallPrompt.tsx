'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from './AuthProvider';
import {
  canPromptInstall,
  isInstallDismissed,
  isInstalled,
  isIosSafari,
  promptInstall,
  rememberInstallDismissed,
  subscribeToInstallState,
} from '@/app/lib/install';

/**
 * Offers staff the one-click install of the dashboard.
 *
 * <p>Two shapes, because the two platforms work differently: on Chrome and
 * Edge we replay the install prompt the browser handed us, so it is one click;
 * on an iPad no such API exists and the best we can do is show the Share-sheet
 * steps. Every other browser gets nothing — a bar that cannot lead to an
 * install is just another thing between staff and the order queue.
 *
 * <p>Only shown to a SIGNED-IN staffer. Unlike the storefront, which waits for
 * a second visit before asking, being signed in is the whole signal needed
 * here: whoever got through the login form is someone who will open this every
 * shift, and that is exactly who should install it. It also keeps the ask off
 * the login screen, which anyone who reaches the URL can see.
 *
 * <p>Renders nothing until an effect has decided, which also keeps it out of
 * the prerendered HTML: installability is a property of the browser, so there
 * is no correct thing to render before hydration.
 *
 * <p>Asks once. Declining is remembered permanently.
 */
export default function InstallPrompt() {
  const { isAuthenticated } = useAuth();
  const [mode, setMode] = useState<'hidden' | 'prompt' | 'ios'>('hidden');
  const [showSteps, setShowSteps] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      setMode('hidden');
      return;
    }
    // Already installed, or already told us no — nothing to ask.
    if (isInstalled() || isInstallDismissed()) return;

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
    // listening — that is what turns an iPad-style bar into a one-click one,
    // and what clears it when an install completes elsewhere.
    return subscribeToInstallState(decide);
  }, [isAuthenticated]);

  const dismiss = useCallback(() => {
    rememberInstallDismissed();
    setMode('hidden');
  }, []);

  const install = useCallback(async () => {
    const outcome = await promptInstall();
    // 'accepted' also hides via the `appinstalled` event, but not every browser
    // fires it reliably; hiding here means the bar never outlives the click.
    // 'dismissed' is the staffer saying no to the browser's own dialog, which
    // is as clear an answer as clicking our dismiss button.
    if (outcome === 'dismissed') rememberInstallDismissed();
    setMode('hidden');
  }, []);

  if (mode === 'hidden') return null;

  return (
    <div className="install-bar" role="region" aria-label="Install this dashboard">
      <span className="install-bar-icon" aria-hidden>
        🖥️
      </span>
      <div className="install-bar-body">
        <p className="install-bar-text">
          <strong>Install this dashboard.</strong> Open it from the{' '}
          {mode === 'ios' ? 'home screen' : 'taskbar'} without the browser bars
          taking up space above the order queue.
        </p>

        {mode === 'ios' && showSteps && (
          <ol className="install-steps">
            <li>Tap the Share button in Safari’s toolbar.</li>
            <li>Scroll down and choose “Add to Home Screen”.</li>
            <li>Tap “Add” — the dashboard appears with your apps.</li>
          </ol>
        )}
      </div>

      <div className="install-bar-actions">
        {mode === 'prompt' ? (
          <button type="button" className="btn install-bar-cta" onClick={install}>
            Install
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-ghost install-bar-cta"
            aria-expanded={showSteps}
            onClick={() => setShowSteps((open) => !open)}
          >
            {showSteps ? 'Hide' : 'How?'}
          </button>
        )}
        <button
          type="button"
          className="install-bar-dismiss"
          onClick={dismiss}
          aria-label="No thanks"
        >
          ✕
        </button>
      </div>
    </div>
  );
}
