/**
 * Loading placeholders.
 *
 * <p>A screen that is still resolving should hold the shape of what is coming,
 * not collapse to the word "Loading…". The difference matters most on checkout,
 * where the authentication check runs on the client after hydration: the page
 * used to render a single line of text and then expand into a full form, which
 * on a phone pushed the first thing the customer looked at halfway down the
 * screen the moment they went to touch it.
 *
 * <p>No hooks and no state, but these DO ship to the browser: checkout is a
 * client component, so anything it imports is part of the client bundle.
 */

/** One shimmering bar. `width` accepts any CSS length or percentage. */
export function Skeleton({
  height = 16,
  width = '100%',
  radius,
}: {
  height?: number | string;
  width?: number | string;
  radius?: number | string;
}) {
  return (
    <span
      className="skeleton"
      aria-hidden="true"
      style={{ height, width, borderRadius: radius }}
    />
  );
}

/**
 * Stand-in for the checkout screen: breadcrumb, title, the delivery-details
 * fieldset, and the order summary. Laid out to the same widths as the real
 * thing so the swap is not a jump.
 */
export function CheckoutSkeleton({ label }: { label: string }) {
  return (
    <div className="skeleton-page" role="status" aria-busy="true">
      {/* The caller owns the wording: it has the translations, this file does not. */}
      <span className="sr-only">{label}</span>
      <Skeleton height={14} width="10rem" />
      <Skeleton height={28} width="14rem" />
      <div className="skeleton-card">
        <Skeleton height={14} width="9rem" />
        <Skeleton height={40} />
        <Skeleton height={14} width="7rem" />
        <Skeleton height={40} />
        <Skeleton height={14} width="11rem" />
        <Skeleton height={40} />
      </div>
      <div className="skeleton-card">
        <Skeleton height={14} width="8rem" />
        <Skeleton height={16} />
        <Skeleton height={16} width="70%" />
        <Skeleton height={20} width="40%" />
      </div>
      <Skeleton height={44} radius={999} />
    </div>
  );
}
