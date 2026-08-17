// i18n configuration for the storefront (cookie-based, no URL routing).
//
// The active language is resolved per-request in i18n/request.ts from:
//   1. an explicit choice saved in the LOCALE_COOKIE (set by the switcher), else
//   2. the device/browser language (Accept-Language header), else
//   3. the default locale.
// Kannada is rendered with a bundled Noto Sans Kannada webfont (see layout.tsx).

export const locales = ['en', 'kn'] as const;
export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = 'en';

// Cookie holding the customer's explicit language choice. Read in
// i18n/request.ts and written by the language switcher (i18n/actions.ts).
export const LOCALE_COOKIE = 'TB_LOCALE';
// Persist the choice for a year.
export const LOCALE_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

// Self-referential labels for the switcher (each shown in its own script).
export const localeLabels: Record<Locale, string> = {
  en: 'English',
  kn: 'ಕನ್ನಡ',
};

export function isLocale(value: unknown): value is Locale {
  return (
    typeof value === 'string' && (locales as readonly string[]).includes(value)
  );
}

/**
 * Pick the best supported locale given an explicit cookie choice (if any) and
 * the browser's Accept-Language header. A saved choice always wins; otherwise we
 * honour the device language (e.g. a Kannada phone gets Kannada), falling back
 * to the default.
 */
export function resolveLocale(
  cookieValue: string | undefined,
  acceptLanguage: string | null | undefined,
): Locale {
  if (isLocale(cookieValue)) return cookieValue;
  return matchAcceptLanguage(acceptLanguage);
}

function matchAcceptLanguage(header: string | null | undefined): Locale {
  if (!header) return defaultLocale;
  // Browsers send languages in descending-q order (e.g. "kn-IN,kn;q=0.9,en;q=0.8"),
  // so take the first supported base language subtag.
  for (const part of header.split(',')) {
    const base = part.trim().split(';')[0].toLowerCase().split('-')[0];
    if (isLocale(base)) return base;
  }
  return defaultLocale;
}
