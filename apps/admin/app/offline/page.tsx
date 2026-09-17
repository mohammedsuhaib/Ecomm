import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Offline — Town Basket Store Admin',
};

/**
 * Offline fallback, served by the service worker when a navigation can't be
 * fulfilled from the network or the shell cache (see app/sw.ts).
 *
 * <p>Says plainly that there is nothing to show rather than offering a stale
 * view of the shop's day. That is the whole posture of the admin worker: it
 * caches the shell so the app opens, and never caches an order, a product or a
 * stock figure, because staff act on what they read here.
 *
 * <p>No AdminShell and no LoginGate around it — both would immediately try to
 * reach an API that is, by definition, unreachable.
 */
export default function OfflinePage() {
  return (
    <div className="queue-empty">
      <div style={{ fontSize: '2.5rem' }} aria-hidden>
        📶
      </div>
      <h1 style={{ fontSize: '1.15rem' }}>No connection</h1>
      <p>
        The dashboard needs a connection to show the order queue, stock and
        prices — it won’t show you a saved copy, because acting on yesterday’s
        queue is worse than seeing nothing.
      </p>
      <p className="muted">
        Check the shop’s Wi-Fi or mobile data, then try again.
      </p>
      <p>
        <a className="btn" href="/">
          Try again
        </a>
      </p>
    </div>
  );
}
