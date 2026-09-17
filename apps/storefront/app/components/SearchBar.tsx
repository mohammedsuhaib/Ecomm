'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';

/**
 * Search box. Navigates to /search?q=… (client navigation), where a server
 * component fetches /products/search. Kept controlled so the header input
 * reflects the active query when on the search page.
 *
 * <p><strong>Clearing it goes back to the shop.</strong> Emptying the box used
 * to do nothing at all — submit returned early on a blank term — so someone
 * who cleared their search sat on the old results with an empty input, which
 * reads as a page that failed to refresh. An empty submit now leaves the
 * search behind, and so does the native clear (✕).
 */
export default function SearchBar() {
  const router = useRouter();
  const params = useSearchParams();
  const [q, setQ] = useState('');
  const t = useTranslations('searchBar');
  const tc = useTranslations('common');
  const inputRef = useRef<HTMLInputElement>(null);

  // Whether a search is what the page is currently showing. Clearing the box
  // only navigates when there is a search TO leave — on the shop or a category
  // page an empty box is just an empty box.
  const activeQuery = params.get('q');

  // Seed from URL when landing on /search?q=…
  useEffect(() => {
    setQ(params.get('q') ?? '');
  }, [params]);

  // The native ✕ inside <input type="search"> fires a `search` event, and
  // nothing else does on a clear — which is why this is not done in onChange:
  // there, backspacing over an old term to type a new one would fire on every
  // keystroke and yank the customer back to the shop mid-word.
  useEffect(() => {
    const input = inputRef.current;
    if (!input) return;
    const onNativeSearch = () => {
      if (input.value.trim() === '' && activeQuery) router.push('/');
    };
    input.addEventListener('search', onNativeSearch);
    return () => input.removeEventListener('search', onNativeSearch);
  }, [activeQuery, router]);

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const term = q.trim();
    if (!term) {
      // Cleared deliberately: show the shop again rather than stale results.
      if (activeQuery) router.push('/');
      return;
    }
    router.push(`/search?q=${encodeURIComponent(term)}`);
  }

  return (
    <form className="search-form" role="search" onSubmit={onSubmit}>
      <input
        ref={inputRef}
        type="search"
        name="q"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder={t('placeholder')}
        aria-label={t('ariaInput')}
      />
      <button type="submit" aria-label={tc('search')}>
        {tc('search')}
      </button>
    </form>
  );
}
