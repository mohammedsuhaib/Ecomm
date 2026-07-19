// PWA install banner. Chrome never guarantees its own install prompt (it's
// gated on engagement heuristics), so the reliable "ask on first visit" is:
// catch beforeinstallprompt, suppress the mini-infobar, and show our own
// Install button that triggers the native dialog via prompt().
//
// External file (not inline) because the site's CSP is default-src 'self'.
(function () {
  'use strict';

  var DISMISS_KEY = 'tb-install-dismissed-at';
  var DISMISS_DAYS = 7;

  function isStandalone() {
    return (
      window.matchMedia('(display-mode: standalone)').matches ||
      window.navigator.standalone === true // iOS Safari
    );
  }

  function recentlyDismissed() {
    try {
      var t = Number(localStorage.getItem(DISMISS_KEY) || 0);
      return t > 0 && Date.now() - t < DISMISS_DAYS * 864e5;
    } catch (e) {
      return false;
    }
  }

  function markDismissed() {
    try {
      localStorage.setItem(DISMISS_KEY, String(Date.now()));
    } catch (e) {
      /* private mode — banner just reappears next visit */
    }
  }

  function buildBanner(text, actionLabel, onAction) {
    var bar = document.createElement('div');
    bar.className = 'install-banner';
    bar.setAttribute('role', 'region');
    bar.setAttribute('aria-label', 'Install app');

    var msg = document.createElement('span');
    msg.className = 'install-banner-text';
    msg.textContent = text;
    bar.appendChild(msg);

    var actions = document.createElement('div');
    actions.className = 'install-banner-actions';

    if (onAction) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'install-banner-btn';
      btn.textContent = actionLabel;
      btn.addEventListener('click', onAction);
      actions.appendChild(btn);
    }

    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'install-banner-close';
    close.setAttribute('aria-label', 'Dismiss install banner');
    close.textContent = '×';
    close.addEventListener('click', function () {
      markDismissed();
      bar.remove();
    });
    actions.appendChild(close);

    bar.appendChild(actions);
    document.body.appendChild(bar);
    return bar;
  }

  if (isStandalone() || recentlyDismissed()) return;

  var deferredPrompt = null;
  var banner = null;

  window.addEventListener('beforeinstallprompt', function (e) {
    e.preventDefault(); // suppress Chrome's unreliable mini-infobar…
    deferredPrompt = e;
    if (banner) return;
    banner = buildBanner(
      'Get the Town Basket app — quick access from your home screen.',
      'Install',
      function () {
        if (!deferredPrompt) return;
        deferredPrompt.prompt(); // …and open the native dialog from OUR button
        deferredPrompt.userChoice.then(function () {
          deferredPrompt = null;
          if (banner) {
            banner.remove();
            banner = null;
          }
        });
      },
    );
  });

  window.addEventListener('appinstalled', function () {
    if (banner) {
      banner.remove();
      banner = null;
    }
  });

  // iOS Safari never fires beforeinstallprompt — show a how-to hint instead.
  if (/iphone|ipad|ipod/i.test(navigator.userAgent)) {
    window.addEventListener('load', function () {
      if (banner || isStandalone()) return;
      banner = buildBanner(
        'Install Town Basket: tap Share, then "Add to Home Screen".',
        null,
        null,
      );
    });
  }
})();
