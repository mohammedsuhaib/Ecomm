/**
 * Loading placeholders for the admin panels.
 *
 * <p>Every panel here fetches on mount and, until now, showed a single centred
 * line of text ("Loading orders…") in place of the list. On the shop's counter
 * tablet that means the whole panel is empty and then snaps to full height, so
 * a staff member who has already reached for the first row taps whatever landed
 * under their finger instead. These hold the list's shape while it loads.
 */

/** One shimmering bar. `width` accepts any CSS length or percentage. */
export function Skeleton({
  height = 16,
  width = '100%',
}: {
  height?: number | string;
  width?: number | string;
}) {
  return <span className="skeleton" aria-hidden="true" style={{ height, width }} />;
}

/**
 * Stand-in for a panel's list or table: {@code rows} bars at roughly a row's
 * height, with the last one short so the block does not read as a solid slab.
 *
 * @param label what is loading, announced to screen readers
 * @param rows how many rows to draw — pick the number the panel usually shows
 *     above the fold, not the page size
 */
export function ListSkeleton({ label, rows = 5 }: { label: string; rows?: number }) {
  return (
    <div className="skeleton-list" role="status" aria-busy="true">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} height={44} width={i === rows - 1 ? '60%' : '100%'} />
      ))}
    </div>
  );
}
