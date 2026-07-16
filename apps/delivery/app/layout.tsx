import type { Metadata, Viewport } from 'next';
import './globals.css';
import { AuthProvider } from './components/AuthProvider';

export const metadata: Metadata = {
  title: 'Town Basket Delivery',
  description: 'Delivery agent portal for Town Basket',
};

// NOTE: never set maximumScale/userScalable here — blocking pinch-zoom fails
// WCAG 1.4.4, and agents read small text on phones outdoors.
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
