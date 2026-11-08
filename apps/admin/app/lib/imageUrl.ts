/**
 * An image link the admin forms will preview and save: `https://` or `http://`,
 * or a path on our own site starting with `/` (the seeded catalogue's pictures
 * are bundled with the storefront). Anything else (`javascript:`, `data:`,
 * `vbscript:`, a bare word) is refused.
 *
 * <p>An `<img src>` does not run `javascript:` in current browsers, so this is
 * not what stops a script: it keeps arbitrary schemes out of a field that is
 * echoed into the page and then stored and served to customers, and the API
 * enforces the same rule so the form is not the only gate.
 */
export function safeImageUrl(raw: string): string | null {
  const url = raw.trim();
  if (/^https?:\/\//i.test(url) || (url.startsWith('/') && !url.startsWith('//'))) {
    return url;
  }
  return null;
}

/** True when the field holds something, but not something {@link safeImageUrl} accepts. */
export function imageUrlRejected(raw: string): boolean {
  return raw.trim() !== '' && safeImageUrl(raw) === null;
}

export const IMAGE_URL_HINT =
  'Use a link starting with https://, or upload a photo.';
