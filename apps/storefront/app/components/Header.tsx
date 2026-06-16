import Link from 'next/link';
import { Suspense } from 'react';
import SearchBar from './SearchBar';
import LocationIndicator from './LocationIndicator';

/** Site header: brand, search, and the delivery-location indicator. */
export default function Header() {
  return (
    <header className="site-header">
      <div className="header-inner">
        <Link href="/" className="brand">
          <span className="logo" aria-hidden>
            🧺
          </span>
          <span>Town Basket</span>
        </Link>
        <div className="header-search">
          {/* SearchBar uses useSearchParams() — needs its own Suspense
              boundary so static pages don't bail out of prerendering. */}
          <Suspense fallback={<div className="search-form" aria-hidden />}>
            <SearchBar />
          </Suspense>
        </div>
        <LocationIndicator />
      </div>
    </header>
  );
}
