import Link from 'next/link';

// Offline fallback served by the service worker when a navigation request
// can't be fulfilled from cache or network (Serwist, see app/sw.ts).
export const metadata = {
  title: 'Offline — Town Basket',
};

export default function OfflinePage() {
  return (
    <div className="empty-state">
      <div style={{ fontSize: '3rem' }} aria-hidden>
        📶
      </div>
      <h1 className="section-title">You’re offline</h1>
      <p>
        Town Basket needs an internet connection to load fresh groceries.
        Please reconnect and try again — pages you’ve already visited may still
        be available.
      </p>
      <p>
        <Link className="btn" href="/">
          Try again
        </Link>
      </p>
    </div>
  );
}
