import type { Metadata } from 'next';

/**
 * One shopper's basket: nothing here is the same for two people, and there is nothing to find in a search result.
 *
 * <p>robots.txt already asks crawlers not to fetch this path, but a URL can be
 * linked from anywhere and a disallowed page can still be indexed on the
 * strength of inbound links alone. The header that actually keeps it out of
 * results is this one, so both are set.
 */
export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function CartLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
