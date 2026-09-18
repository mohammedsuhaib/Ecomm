import type { Metadata } from 'next';

/**
 * One order, reached by an unguessable tracking token. Indexing it would publish the token — the whole point of which is that only the customer has it.
 *
 * <p>robots.txt already asks crawlers not to fetch this path, but a URL can be
 * linked from anywhere and a disallowed page can still be indexed on the
 * strength of inbound links alone. The header that actually keeps it out of
 * results is this one, so both are set.
 */
export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function OrderLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
