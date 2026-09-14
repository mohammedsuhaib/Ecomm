// Display helpers. Prices arrive as decimal rupees and are formatted with ₹.

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
  minimumFractionDigits: 2,
});

/** Format a rupee amount with consistent paise, e.g. 49 -> "₹49.00", 49.5 -> "₹49.50". */
export function formatRupees(amount: number): string {
  return inr.format(amount);
}

/**
 * Difference between two rupee amounts, rounded to whole paise to avoid binary
 * floating-point drift (e.g. 499 - 498.7 must be 0.30, never 0.30000000004).
 */
export function subtractRupees(a: number, b: number): number {
  return Math.round((a - b) * 100) / 100;
}

/**
 * The app's locale as a BCP-47 tag for Intl. The store serves one city, so both
 * languages keep the IN region and its conventions (Indian digit grouping, ₹).
 */
function localeTag(locale: string): string {
  return locale === 'kn' ? 'kn-IN' : 'en-IN';
}

/**
 * Date and time for the order screens, e.g. "14 Sept, 10:15 am".
 *
 * The locale is a parameter rather than a constant because this string is
 * embedded in translated sentences ("ಆರ್ಡರ್ #{code} · {time}"). Hardcoding
 * 'en-IN' put an English month abbreviation — "Sept" — inside otherwise
 * Kannada text on the screen every customer lands on after checkout; kn-IN
 * renders it "ಸೆಪ್ಟೆಂ".
 *
 * Returns '' for an unparseable timestamp so callers can leave it out rather
 * than print "Invalid Date".
 */
export function formatDateTime(iso: string, locale: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString(localeTag(locale), {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

/** Round metres to a friendly distance string, e.g. "1.2 km" or "850 m". */
export function formatDistance(meters: number): string {
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
  return `${Math.round(meters)} m`;
}

/** Format a single "HH:mm" 24-hour time as a 12-hour clock, e.g. "08:00" -> "8 AM". */
export function formatClock(time: string): string {
  const date = new Date(`1970-01-01T${time}`);
  if (Number.isNaN(date.getTime())) return time; // unparseable — show as-is
  return date.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

/**
 * Format store opening hours from 24-hour "HH:mm" strings into a friendly
 * 12-hour range, e.g. formatHours("08:00", "21:00") -> "8 AM – 9 PM".
 * Display only; no data change.
 */
export function formatHours(open: string, close: string): string {
  return `${formatClock(open)} – ${formatClock(close)}`;
}
