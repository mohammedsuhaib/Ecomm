'use server';

import { cookies } from 'next/headers';
import { LOCALE_COOKIE, LOCALE_COOKIE_MAX_AGE, isLocale } from './config';

/**
 * Persist the customer's explicit language choice. Called by the language
 * switcher; the client refreshes afterwards so server components re-render in
 * the chosen language.
 */
export async function setLocaleCookie(locale: string): Promise<void> {
  if (!isLocale(locale)) return;
  cookies().set(LOCALE_COOKIE, locale, {
    path: '/',
    maxAge: LOCALE_COOKIE_MAX_AGE,
    sameSite: 'lax',
  });
}
