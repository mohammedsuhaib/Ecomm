import type { Metadata, Viewport } from 'next';
import { Suspense } from 'react';
import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import './globals.css';
import Header from './components/Header';
import Footer from './components/Footer';
import LocationGate from './components/LocationGate';
import StoreClosedBanner from './components/StoreClosedBanner';
import CartProvider from './components/CartProvider';
import AuthProvider from './components/AuthProvider';
import ChunkErrorRecovery from './components/ChunkErrorRecovery';

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
  themeColor: '#2e7d32',
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
            {/* eslint-disable-next-line @next/next/no-page-custom-font --
                the rule is about the Pages Router, where a font link outside
                _document.js loads for one page only. This is the App Router's
                ROOT layout, so the link is on every route by construction. */}
            <link
              href="https://fonts.googleapis.com/css2?family=Noto+Sans+Kannada:wght@400;500;600;700&display=swap"
              rel="stylesheet"
            />
          </>
        )}
        {/* Outside every provider and boundary: a chunk that goes missing
            because a deploy landed under an open session fails in the router,
            not in a render, so no error boundary sees it. Listens from here and
            renders nothing. */}
        <ChunkErrorRecovery />
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
                {/* Sitewide: while the shop is shut, say so on every page
                    rather than letting the customer discover it at checkout. */}
                <StoreClosedBanner />
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
