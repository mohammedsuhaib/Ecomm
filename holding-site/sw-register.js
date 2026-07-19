// Service-worker registration. External file (not inline) because the site's
// CSP is default-src 'self' with no 'unsafe-inline' for scripts.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('/sw.js').catch(function () {
      // Non-fatal: the site works fine without offline support.
    });
  });
}
