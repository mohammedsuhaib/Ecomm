'use client';

import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { SORT_OPTIONS } from '@/app/lib/sort';
import type { ProductSort } from '@/app/lib/api';

/**
 * Sort dropdown for product listings (home / category / search). Updates the
 * `?sort=` query param via client navigation so the SSR page re-fetches with
 * the chosen order. Other params (e.g. `q`) are preserved; paging resets to the
 * first page on a sort change. `basePath`/`sort` come from the server page so
 * the control matches the rendered list and works without JS for the initial
 * render (the <select> reflects the current value).
 */
export default function SortControl({
  basePath,
  sort,
}: {
  basePath: string;
  sort?: ProductSort;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  function onChange(value: string) {
    const next = new URLSearchParams(params.toString());
    if (value) next.set('sort', value);
    else next.delete('sort');
    // A new sort reshuffles the full set — go back to the first page.
    next.delete('page');
    const qs = next.toString();
    // Prefer the live pathname (client) but fall back to the page's basePath.
    router.push(`${pathname || basePath}${qs ? `?${qs}` : ''}`);
  }

  return (
    <label className="sort-control">
      <span className="sort-control-label">Sort</span>
      <select
        value={sort ?? ''}
        onChange={(e) => onChange(e.target.value)}
        aria-label="Sort products"
      >
        <option value="">Recommended</option>
        {SORT_OPTIONS.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}
