import type { Metadata, Viewport } from 'next';
import './globals.css';
import { AuthProvider } from './components/AuthProvider';

export const metadata: Metadata = {
  title: 'Town Basket Delivery',
  description: 'Delivery agent portal for Town Basket',
  manifest: '/manifest.webmanifest',
  appleWebApp: {
    capable: true,
    title: 'TB Delivery',
    statusBarStyle: 'default',
  },
  icons: {
    icon: '/icons/icon-192.png',
    apple: '/icons/icon-192.png',
  },
};

// NOTE: never set maximumScale/userScalable here — blocking pinch-zoom fails
// WCAG 1.4.4, and agents read small text on phones outdoors.
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#1a56db',
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
