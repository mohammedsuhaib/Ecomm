import type { ProductSort } from './api';

/** Sort options exposed in the listing dropdown, in display order (labels come from i18n). */
export const SORT_OPTIONS: readonly ProductSort[] = [
  'name',
  'name_desc',
  'price_asc',
  'price_desc',
  'discount',
];

const VALID = new Set<string>(SORT_OPTIONS);

/**
 * Validate a raw `?sort=` value, returning a known ProductSort or undefined
 * (which the API treats as the backend's default order).
 */
export function parseSort(raw?: string | null): ProductSort | undefined {
  return raw && VALID.has(raw) ? (raw as ProductSort) : undefined;
}
