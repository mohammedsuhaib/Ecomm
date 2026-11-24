import 'server-only';
import townbasket from '@/brands/townbasket.json';

/**
 * Everything that makes this storefront THIS client's shop: name, colours,
 * logo, public addresses. One JSON file per client under `brands/`, chosen per
 * deployment by the runtime environment variable {@code BRAND}.
 *
 * <p>Runtime, not build time, on purpose and like {@code SITE_URL} (see
 * site.ts): one image serves every client, and which one a container is
 * serving is a fact about the deployment. Server-only: client components get
 * the few values they need from {@link BrandProvider}, so a brand file never
 * ships to the browser.
 *
 * <p>Adding a client: see brands/README.md. An unknown {@code BRAND} throws
 * rather than falling back to the default, because quietly showing one client's
 * name and logo on another client's shop is worse than an error page.
 */
export interface Brand {
  /** Lower-case id, the {@code BRAND} value and the file name. */
  id: string;
  /** The shop's name as customers read it. */
  name: string;
  /** Lower-case, no spaces: used in file names such as invoice downloads. */
  slug: string;
  /** Canonical public origin when {@code SITE_URL} is not set. */
  siteUrl: string;
  /** Where the published policy pages (terms, privacy, …) are served. */
  policyBaseUrl: string;
  /** The one-line description used for the install manifest. */
  description: string;
  /** Alt text for the social-share picture (1200x630) shown when a link is shared. */
  shareImageAlt: string;
  colors: {
    /** Header and primary buttons. Dark enough for white text (WCAG AA). */
    primary: string;
    /** Hover and pressed state, headings. */
    primaryDark: string;
    /** Tinted backgrounds. */
    primaryLight: string;
    /** Install splash screen ground; match the icons' own background. */
    splashBackground: string;
  };
  /** Paths under public/. */
  assets: {
    logoMark: string;
    icon192: string;
    icon512: string;
    iconMaskable512: string;
    /** The 1200x630 picture shown when a link to the shop is shared. */
    shareImage: string;
    /** Browser tab icon, 128x128 PNG. */
    favicon: string;
    /** iPhone home-screen icon, 180x180 PNG. */
    appleTouchIcon: string;
  };
}

/** Every brand this build knows. A new client is one file plus one line here. */
const BRANDS: Record<string, Brand> = {
  townbasket,
};

export const DEFAULT_BRAND_ID = 'townbasket';

/** The brand this deployment serves. */
export function brand(): Brand {
  const id = (process.env.BRAND ?? '').trim().toLowerCase() || DEFAULT_BRAND_ID;
  const found = BRANDS[id];
  if (!found) {
    throw new Error(
      `Unknown BRAND "${id}". Known brands: ${Object.keys(BRANDS).join(', ')}. ` +
        'Add brands/<id>.json and register it in app/lib/brand.ts.',
    );
  }
  return found;
}

/** The part of the brand a client component may know. */
export interface PublicBrand {
  name: string;
  slug: string;
}

export function publicBrand(): PublicBrand {
  const { name, slug } = brand();
  return { name, slug };
}

/**
 * The brand's social-share picture for Open Graph. A page that sets its own
 * `openGraph` replaces the layout's wholesale (Next merges one level deep), so
 * every page that does must pass these itself, or its shared links lose the
 * picture.
 */
export function shareImages(): { url: string; width: number; height: number; alt: string }[] {
  const { assets, shareImageAlt } = brand();
  return [{ url: assets.shareImage, width: 1200, height: 630, alt: shareImageAlt }];
}

/** The brand's colours as the CSS variables the stylesheet already uses. */
export function brandCssVariables(): Record<string, string> {
  const { colors } = brand();
  return {
    '--green': colors.primary,
    '--green-dark': colors.primaryDark,
    '--green-light': colors.primaryLight,
  };
}
