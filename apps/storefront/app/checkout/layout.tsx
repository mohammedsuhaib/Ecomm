import type { Metadata } from 'next';

/**
 * A form for placing an order, carrying a name, phone and address. It has no business in a search index.
 *
 * <p>robots.txt already asks crawlers not to fetch this path, but a URL can be
 * linked from anywhere and a disallowed page can still be indexed on the
 * strength of inbound links alone. The header that actually keeps it out of
 * results is this one, so both are set.
 */
export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function CheckoutLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
