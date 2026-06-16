/** Veg / non-veg indicator (FSSAI-style square dot) driven by `vegMarker`. */
export default function VegMarker({ veg }: { veg: boolean }) {
  return (
    <span
      className={`veg-dot ${veg ? 'veg' : 'nonveg'}`}
      role="img"
      aria-label={veg ? 'Vegetarian' : 'Non-vegetarian'}
      title={veg ? 'Veg' : 'Non-veg'}
    />
  );
}
