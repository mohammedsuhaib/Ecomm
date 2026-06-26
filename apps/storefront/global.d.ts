import type en from './messages/en.json';
import type { Locale } from './i18n/config';

// Type-safe translations: t('key') usages are checked against en.json, and the
// active locale is constrained to the supported set.
declare module 'next-intl' {
  interface AppConfig {
    Messages: typeof en;
    Locale: Locale;
  }
}
