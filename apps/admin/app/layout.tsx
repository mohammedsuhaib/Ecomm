import type { Metadata, Viewport } from 'next';

export const metadata: Metadata = {
  title: 'Town Basket — Store Admin',
  description: 'Order queue, catalogue, inventory and store configuration for Town Basket staff.',
};

export const viewport: Viewport = {
  themeColor: '#1f3a8a',
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
