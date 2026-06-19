import type { ProductSort } from './api';

/** Sort options exposed in the listing dropdown, in display order. */
export const SORT_OPTIONS: { value: ProductSort; label: string }[] = [
  { value: 'name', label: 'A–Z' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'discount', label: 'Biggest discount' },
];

const VALID = new Set<string>(SORT_OPTIONS.map((o) => o.value));

/**
 * Validate a raw `?sort=` value, returning a known ProductSort or undefined
 * (which the API treats as the backend's default order).
 */
export function parseSort(raw?: string | null): ProductSort | undefined {
  return raw && VALID.has(raw) ? (raw as ProductSort) : undefined;
}
