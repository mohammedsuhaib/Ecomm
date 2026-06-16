import type { Metadata, Viewport } from 'next';
import { Suspense } from 'react';
import './globals.css';
import Header from './components/Header';
import Footer from './components/Footer';
import LocationGate from './components/LocationGate';
import CartProvider from './components/CartProvider';

export const metadata: Metadata = {
  title: 'Town Basket — Groceries delivered',
  description:
    'Order groceries from your neighbourhood supermarket, delivered fast within 5 km.',
  manifest: '/manifest.webmanifest',
  applicationName: 'Town Basket',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'default',
    title: 'Town Basket',
  },
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
        {/* LocationGate is the provider for the whole shell so the header's
            location pill can re-open it. It prompts for location on first
            visit and blocks the catalogue when out of range; otherwise it
            renders children. */}
        <LocationGate>
          {/* CartProvider holds the server cart id + state for the header
              badge, add-to-cart controls, cart and checkout pages. */}
          <CartProvider>
            <Header />
            <main className="wrap">
              {/* SearchBar/useSearchParams need a Suspense boundary. */}
              <Suspense fallback={null}>{children}</Suspense>
            </main>
            <Footer />
          </CartProvider>
        </LocationGate>
      </body>
    </html>
  );
}
