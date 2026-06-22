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

/** Round metres to a friendly distance string, e.g. "1.2 km" or "850 m". */
export function formatDistance(meters: number): string {
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
  return `${Math.round(meters)} m`;
}

/** Format a single "HH:mm" 24-hour time as a 12-hour clock, e.g. "08:00" -> "8 AM". */
function formatClock(time: string): string {
  const [h, m] = time.split(':');
  const hour = Number.parseInt(h ?? '', 10);
  const minute = Number.parseInt(m ?? '0', 10) || 0;
  if (Number.isNaN(hour)) return time; // unparseable — show as-is
  const period = hour >= 12 ? 'PM' : 'AM';
  let hour12 = hour % 12;
  if (hour12 === 0) hour12 = 12;
  return minute > 0
    ? `${hour12}:${String(minute).padStart(2, '0')} ${period}`
    : `${hour12} ${period}`;
}

/**
 * Format store opening hours from 24-hour "HH:mm" strings into a friendly
 * 12-hour range, e.g. formatHours("08:00", "21:00") -> "8 AM – 9 PM".
 * Display only; no data change.
 */
export function formatHours(open: string, close: string): string {
  return `${formatClock(open)} – ${formatClock(close)}`;
}
