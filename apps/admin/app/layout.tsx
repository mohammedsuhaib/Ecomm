import type { Metadata, Viewport } from 'next';
import './globals.css';
import { AuthProvider } from './components/AuthProvider';

export const metadata: Metadata = {
  title: 'Town Basket — Store Admin',
  description: 'Order queue, catalogue, inventory and store configuration for Town Basket staff.',
  // Installable PWA: staff run the dashboard from the taskbar or home screen
  // without browser chrome. See app/manifest.ts and app/sw.ts.
  manifest: '/manifest.webmanifest',
  applicationName: 'TB Admin',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'default',
    title: 'TB Admin',
  },
  icons: {
    icon: '/icons/icon-192.png',
    apple: '/icons/icon-192.png',
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
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
