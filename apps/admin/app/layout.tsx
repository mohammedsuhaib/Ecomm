import type { Metadata, Viewport } from 'next';
import './globals.css';
import { AuthProvider } from './components/AuthProvider';

export const metadata: Metadata = {
  title: 'Town Basket — Store Admin',
  description: 'Order queue, catalogue, inventory and store configuration for Town Basket staff.',
};

export const viewport: Viewport = {
  themeColor: '#2e7d32',
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
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
