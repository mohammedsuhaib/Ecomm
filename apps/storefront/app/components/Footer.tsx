import Link from 'next/link';

/** Site footer with brand line and a few navigation links. */
export default function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <nav aria-label="Footer">
          <Link href="/">Home</Link>
          <Link href="/search?q=">Search</Link>
        </nav>
        <div className="copyright">
          © {year} Town Basket — fresh groceries, delivered fast within 5&nbsp;km.
        </div>
      </div>
    </footer>
  );
}
