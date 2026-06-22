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
