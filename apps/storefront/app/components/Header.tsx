import Link from 'next/link';
import { Suspense } from 'react';
import SearchBar from './SearchBar';
import LocationIndicator from './LocationIndicator';
import CartIndicator from './CartIndicator';
import AccountIndicator from './AccountIndicator';
import BackButton from './BackButton';
import LanguageSwitcher from './LanguageSwitcher';

/** Site header: brand, search, and the delivery-location indicator. */
export default function Header() {
  return (
    <header className="site-header">
      <div className="header-inner">
        {/* Mobile-only back control (hidden ≥640px); needs its own Suspense
            boundary because it reads usePathname(). */}
        <Suspense fallback={null}>
          <BackButton />
        </Suspense>
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
        <LanguageSwitcher />
        <AccountIndicator />
        <CartIndicator />
      </div>
    </header>
  );
}
