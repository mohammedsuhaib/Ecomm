'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useEffect } from 'react';

/**
 * Search box. Navigates to /search?q=… (client navigation), where a server
 * component fetches /products/search. Kept controlled so the header input
 * reflects the active query when on the search page.
 */
export default function SearchBar() {
  const router = useRouter();
  const params = useSearchParams();
  const [q, setQ] = useState('');

  // Seed from URL when landing on /search?q=…
  useEffect(() => {
    setQ(params.get('q') ?? '');
  }, [params]);

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const term = q.trim();
    if (!term) return;
    router.push(`/search?q=${encodeURIComponent(term)}`);
  }

  return (
    <form className="search-form" role="search" onSubmit={onSubmit}>
      <input
        type="search"
        name="q"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search for groceries…"
        aria-label="Search products"
      />
      <button type="submit" aria-label="Search">
        Search
      </button>
    </form>
  );
}
