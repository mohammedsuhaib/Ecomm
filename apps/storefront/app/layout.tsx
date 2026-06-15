import type { Metadata, Viewport } from 'next';

export const metadata: Metadata = {
  title: 'Town Basket — Groceries delivered',
  description:
    'Order groceries from your neighbourhood supermarket, delivered fast within the delivery radius.',
  manifest: '/manifest.webmanifest',
};

export const viewport: Viewport = {
  themeColor: '#1f8a4c',
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
