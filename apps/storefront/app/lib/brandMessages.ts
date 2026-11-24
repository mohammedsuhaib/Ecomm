/**
 * Fill the {@code {brand}} token in the translation files with the shop's name.
 *
 * <p>Done once when the messages load, before they reach the ICU formatter, so
 * the hundred or so places that show the name need no extra argument at each
 * call site. The name is made safe for ICU first: braces and angle brackets
 * (placeholders and rich-text tags there) are dropped, and a straight
 * apostrophe is doubled, because a lone one before a brace starts a quoted
 * span.
 */
export function withBrand<T>(messages: T, brandName: string): T {
  const safe = brandName.replace(/[{}<>]/g, '').replace(/'/g, "''");
  const fill = (value: unknown): unknown => {
    if (typeof value === 'string') return value.split('{brand}').join(safe);
    if (Array.isArray(value)) return value.map(fill);
    if (value && typeof value === 'object') {
      return Object.fromEntries(
        Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, fill(v)]),
      );
    }
    return value;
  };
  return fill(messages) as T;
}
