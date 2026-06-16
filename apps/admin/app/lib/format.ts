// Display helpers. Prices arrive as decimal rupees and are formatted with ₹.

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
  minimumFractionDigits: 0,
});

/** Format a rupee amount, e.g. 49 -> "₹49", 49.5 -> "₹49.50". */
export function formatRupees(amount: number): string {
  return inr.format(amount);
}

/** Short date/time for the order queue, e.g. "16 Jun, 3:45 PM". */
export function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}
