import { getRequestConfig } from 'next-intl/server';
import { cookies, headers } from 'next/headers';
import { LOCALE_COOKIE, resolveLocale } from './config';

// Resolves the request locale (cookie → Accept-Language → default) and loads
// the matching messages. Reading cookies/headers opts pages into dynamic
// rendering, which is expected for cookie-based i18n without URL routing.
export default getRequestConfig(async () => {
  const cookieLocale = cookies().get(LOCALE_COOKIE)?.value;
  const acceptLanguage = headers().get('accept-language');
  const locale = resolveLocale(cookieLocale, acceptLanguage);

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
  };
});
