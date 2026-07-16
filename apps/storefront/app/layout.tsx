import type { Metadata, Viewport } from 'next';
import { Suspense } from 'react';
import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import './globals.css';
import Header from './components/Header';
import Footer from './components/Footer';
import LocationGate from './components/LocationGate';
import CartProvider from './components/CartProvider';
import AuthProvider from './components/AuthProvider';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('metadata');
  return {
    title: t('title'),
    description: t('description'),
    manifest: '/manifest.webmanifest',
    applicationName: 'Town Basket',
    appleWebApp: {
      capable: true,
      statusBarStyle: 'default',
      title: 'Town Basket',
    },
  };
}

export const viewport: Viewport = {
  themeColor: '#f9a825',
  width: 'device-width',
  initialScale: 1,
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // Locale + messages resolved from the cookie/Accept-Language (i18n/request.ts).
  const locale = await getLocale();
  const messages = await getMessages();
  const tc = await getTranslations('common');

  return (
    <html lang={locale}>
      <body>
        {/* Keyboard users skip the header's 7+ tab stops; visible on focus. */}
        <a href="#main" className="skip-link">
          {tc('skipToContent')}
        </a>
        {/* Kannada needs a script-capable webfont; only load it when the active
            language is Kannada so English visitors aren't charged for it. The
            CSS font stack falls through to 'Noto Sans Kannada' for Kannada
            glyphs (globals.css). */}
        {locale === 'kn' && (
          <>
            <link rel="preconnect" href="https://fonts.googleapis.com" />
            <link
              rel="preconnect"
              href="https://fonts.gstatic.com"
              crossOrigin="anonymous"
            />
            <link
              href="https://fonts.googleapis.com/css2?family=Noto+Sans+Kannada:wght@400;500;600;700&display=swap"
              rel="stylesheet"
            />
          </>
        )}
        <NextIntlClientProvider locale={locale} messages={messages}>
          {/* LocationGate is the provider for the whole shell so the header's
              location pill can re-open it. It prompts for location on first
              visit and blocks the catalogue when out of range; otherwise it
              renders children. */}
          <LocationGate>
            {/* AuthProvider sits OUTSIDE CartProvider so the cart can read auth
                (e.g. login-time cart merge) and the header can show the account
                link. It holds the customer session (tokens in localStorage). */}
            <AuthProvider>
              {/* CartProvider holds the server cart id + state for the header
                  badge, add-to-cart controls, cart and checkout pages. */}
              <CartProvider>
                <Header />
                <main id="main" className="wrap">
                  {/* SearchBar/useSearchParams need a Suspense boundary. */}
                  <Suspense fallback={null}>{children}</Suspense>
                </main>
                <Footer />
              </CartProvider>
            </AuthProvider>
          </LocationGate>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
