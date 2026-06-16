import { formatRupees } from '@/app/lib/format';

/**
 * Shows selling price with the MRP struck through when present (and higher).
 */
export default function PriceTag({
  sellingPrice,
  mrp,
}: {
  sellingPrice: number;
  mrp: number | null;
}) {
  const showMrp = mrp != null && mrp > sellingPrice;
  return (
    <span className="price-row">
      <span className="price">{formatRupees(sellingPrice)}</span>
      {showMrp && <span className="mrp">{formatRupees(mrp)}</span>}
    </span>
  );
}
