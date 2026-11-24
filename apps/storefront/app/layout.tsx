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
import InstallPrompt from './components/InstallPrompt';
import StickyCartBar from './components/StickyCartBar';
import { brand, brandCssVariables, publicBrand, shareImages } from './lib/brand';
import { BrandProvider } from './components/BrandProvider';
import { isNoIndex, siteUrl } from './lib/site';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('metadata');
  const locale = await getLocale();
  const { name, assets, shareImageAlt } = brand();
  return {
    // Everything relative in a page's metadata — canonicals, Open Graph
    // images — is resolved against this. Without it Next emits relative OG
    // image URLs, which no crawler or chat app will fetch, and the shop's
    // shared links come out blank. Runtime value: see lib/site.ts.
    metadataBase: new URL(siteUrl()),
    title: t('title'),
    description: t('description'),
    manifest: '/manifest.webmanifest',
    // The brand's own files: Next's icon.png / apple-icon.png convention files
    // cannot change per deployment.
    icons: {
      icon: [{ url: assets.favicon, sizes: '128x128', type: 'image/png' }],
      apple: [{ url: assets.appleTouchIcon, sizes: '180x180', type: 'image/png' }],
    },
    applicationName: name,
    appleWebApp: {
      capable: true,
      statusBarStyle: 'default',
      title: name,
    },
    openGraph: {
      type: 'website',
      siteName: name,
      // The brand's own file, not Next's opengraph-image convention file, which
      // cannot change per deployment.
      images: shareImages(),
      title: t('title'),
      description: t('description'),
      locale: locale === 'kn' ? 'kn_IN' : 'en_IN',
      url: '/',
    },
    twitter: {
      card: 'summary_large_image',
      images: [{ url: assets.shareImage, alt: shareImageAlt }],
      title: t('title'),
      description: t('description'),
    },
    // A non-production deployment says so in every page's head, on top of the
    // X-Robots-Tag its proxy sends and the robots.txt it serves.
    ...(isNoIndex() ? { robots: { index: false, follow: false } } : {}),
  };
}

export function generateViewport(): Viewport {
  return {
    themeColor: brand().colors.primary,
    width: 'device-width',
    initialScale: 1,
  };
}

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
    <html lang={locale} style={brandCssVariables() as React.CSSProperties}>
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
          <BrandProvider brand={publicBrand()}>
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
                {/* Offers the home-screen install where the browser can do
                    one. Renders nothing once dismissed, or
                    when already installed — see the component. Below the
                    closed banner on purpose: trading hours outrank it. */}
                <InstallPrompt />
                <main id="main" className="wrap">
                  {/* SearchBar/useSearchParams need a Suspense boundary. */}
                  <Suspense fallback={null}>{children}</Suspense>
                </main>
                <Footer />
                {/* Phones only: the way to the cart, kept under the thumb. */}
                <StickyCartBar />
              </CartProvider>
            </AuthProvider>
          </LocationGate>
          </BrandProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
