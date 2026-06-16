import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="empty-state">
      <div style={{ fontSize: '3rem' }} aria-hidden>
        🧺
      </div>
      <h1 className="section-title">Page not found</h1>
      <p>We couldn’t find what you were looking for.</p>
      <p>
        <Link className="btn" href="/">
          Back to shopping
        </Link>
      </p>
    </div>
  );
}
