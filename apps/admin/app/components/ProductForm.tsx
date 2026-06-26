'use client';

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  ApiError,
  AuthRequiredError,
  createProduct,
  createVariant,
  deleteVariant,
  getAdminProduct,
  updateProduct,
  updateVariant,
  type VariantWriteRequest,
} from '@/app/lib/api';
import type { AdminProduct, Category } from '@/app/lib/types';

// A variant row in the editor. `id` is present for variants that already exist
// on the server (edit mode); null for rows added in the form (need a POST). All
// numeric inputs are kept as strings so the fields can be cleared while typing.
interface VariantRow {
  id: number | null;
  label: string;
  sellingPrice: string;
  costPrice: string;
  mrp: string;
  available: boolean;
  sortOrder: string;
}

function blankVariant(sortOrder: number): VariantRow {
  return {
    id: null,
    label: '',
    sellingPrice: '',
    costPrice: '',
    mrp: '',
    available: true,
    sortOrder: String(sortOrder),
  };
}

function rowFromVariant(v: AdminProduct['variants'][number]): VariantRow {
  return {
    id: v.id,
    label: v.label,
    sellingPrice: String(v.sellingPrice),
    costPrice: String(v.costPrice),
    mrp: v.mrp == null ? '' : String(v.mrp),
    available: v.available,
    sortOrder: String(v.sortOrder),
  };
}

// Parse a money/number string to a finite number, or null if blank/invalid.
function toNumber(s: string): number | null {
  const t = s.trim();
  if (t === '') return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

function variantRowValid(r: VariantRow): boolean {
  const sell = toNumber(r.sellingPrice);
  const cost = toNumber(r.costPrice);
  const mrp = toNumber(r.mrp);
  return (
    r.label.trim().length > 0 &&
    sell !== null &&
    sell >= 0 &&
    cost !== null &&
    cost >= 0 &&
    (r.mrp.trim() === '' || (mrp !== null && mrp >= 0))
  );
}

function rowToWrite(r: VariantRow): VariantWriteRequest {
  return {
    label: r.label.trim(),
    sellingPrice: toNumber(r.sellingPrice) ?? 0,
    costPrice: toNumber(r.costPrice) ?? 0,
    mrp: r.mrp.trim() === '' ? null : toNumber(r.mrp),
    available: r.available,
    sortOrder: toNumber(r.sortOrder) ?? 0,
  };
}

/**
 * Create/edit form for a catalogue product with an inline variants editor.
 *
 * Create: all fields + variants are sent in one POST /products call.
 * Edit: the product's own fields go via PUT /products/{id}; variants are then
 * reconciled against the server using the variant endpoints — added rows POST,
 * changed existing rows PUT, and removed rows DELETE. Validation (name +
 * category required, ≥1 variant with a label and non-negative selling/cost
 * price) gates the submit button. Mirrors ChangePassword's busy/error/success.
 */
export default function ProductForm({
  productId,
  categories,
  onSaved,
  onClose,
  onAuthExpired,
}: {
  productId: number | null;
  categories: Category[];
  onSaved: () => Promise<void> | void;
  onClose: () => void;
  onAuthExpired: () => void;
}) {
  const isEdit = productId !== null;

  const [loading, setLoading] = useState(isEdit);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Product fields.
  const [name, setName] = useState('');
  const [nameKn, setNameKn] = useState('');
  const [slug, setSlug] = useState('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [description, setDescription] = useState('');
  const [vegMarker, setVegMarker] = useState(true);
  const [imageUrl, setImageUrl] = useState('');
  const [available, setAvailable] = useState(true);
  const [featured, setFeatured] = useState(false);

  // Variants.
  const [variants, setVariants] = useState<VariantRow[]>([blankVariant(0)]);
  // Ids of server variants that have been removed in the form (edit mode).
  const [removedIds, setRemovedIds] = useState<number[]>([]);

  // Load the product to edit.
  useEffect(() => {
    if (!isEdit) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const p = await getAdminProduct(productId);
        if (cancelled) return;
        setName(p.name);
        setNameKn(p.nameKn ?? '');
        setSlug(p.slug);
        setCategoryId(p.categoryId);
        setDescription(p.description ?? '');
        setVegMarker(p.vegMarker);
        setImageUrl(p.imageUrl ?? '');
        setAvailable(p.available);
        setFeatured(p.featured);
        const rows = [...p.variants]
          .sort((a, b) => a.sortOrder - b.sortOrder)
          .map(rowFromVariant);
        setVariants(rows.length > 0 ? rows : [blankVariant(0)]);
      } catch (err) {
        if (cancelled) return;
        if (err instanceof AuthRequiredError) onAuthExpired();
        else setLoadError('Could not load this product. Go back and retry.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isEdit, productId, onAuthExpired]);

  // Default the category select to the first category in create mode.
  useEffect(() => {
    if (!isEdit && categoryId === '' && categories.length > 0) {
      setCategoryId(categories[0].id);
    }
  }, [isEdit, categoryId, categories]);

  function updateVariantRow(idx: number, patch: Partial<VariantRow>) {
    setVariants((rows) =>
      rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)),
    );
  }

  function addVariantRow() {
    setVariants((rows) => [...rows, blankVariant(rows.length)]);
  }

  function removeVariantRow(idx: number) {
    setVariants((rows) => {
      const target = rows[idx];
      if (target?.id != null) {
        setRemovedIds((ids) => [...ids, target.id as number]);
      }
      const next = rows.filter((_, i) => i !== idx);
      return next.length > 0 ? next : [blankVariant(0)];
    });
  }

  const validVariants = useMemo(
    () => variants.filter(variantRowValid),
    [variants],
  );

  const canSubmit =
    !busy &&
    !loading &&
    name.trim().length > 0 &&
    categoryId !== '' &&
    validVariants.length >= 1 &&
    // Every non-empty row must be valid (don't silently drop a half-filled row).
    variants.every(
      (r) =>
        variantRowValid(r) ||
        (r.label.trim() === '' &&
          r.sellingPrice.trim() === '' &&
          r.costPrice.trim() === ''),
    );

  function mapError(err: unknown): string {
    if (err instanceof AuthRequiredError) return 'Session expired — please log in again.';
    if (err instanceof ApiError) {
      if (err.status === 409) return 'A product with that name or slug already exists.';
      if (err.status === 400 || err.status === 422)
        return 'Some fields were rejected by the server. Re-check and try again.';
      if (err.status === 0) return 'Could not reach the server. Check your connection.';
    }
    return 'Could not save the product. Please try again.';
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!canSubmit || typeof categoryId !== 'number') return;
    setBusy(true);
    setError(null);

    // Only persist rows that are actually filled in & valid.
    const rowsToSave = variants.filter(variantRowValid);

    try {
      if (!isEdit) {
        await createProduct({
          name: name.trim(),
          nameKn: nameKn.trim() === '' ? null : nameKn.trim(),
          slug: slug.trim() === '' ? undefined : slug.trim(),
          categoryId,
          description: description.trim() === '' ? null : description.trim(),
          vegMarker,
          imageUrl: imageUrl.trim() === '' ? null : imageUrl.trim(),
          available,
          featured,
          variants: rowsToSave.map(rowToWrite),
        });
      } else {
        const id = productId as number;
        // 1) Product's own fields.
        await updateProduct(id, {
          name: name.trim(),
          nameKn: nameKn.trim() === '' ? null : nameKn.trim(),
          categoryId,
          description: description.trim() === '' ? null : description.trim(),
          vegMarker,
          imageUrl: imageUrl.trim() === '' ? null : imageUrl.trim(),
          available,
          featured,
        });
        // 2) Removed variants.
        for (const variantId of removedIds) {
          await deleteVariant(id, variantId);
        }
        // 3) Added (no id) → POST; existing → PUT (covers availability, prices…).
        for (const row of rowsToSave) {
          const write = rowToWrite(row);
          if (row.id == null) {
            await createVariant(id, write);
          } else {
            await updateVariant(id, row.id, write);
          }
        }
      }
      await onSaved();
    } catch (err) {
      setError(mapError(err));
      if (err instanceof AuthRequiredError) onAuthExpired();
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="product-form-panel">
      <div className="cat-panel-head">
        <h2 className="cat-panel-title">
          {isEdit ? 'Edit product' : 'New product'}
        </h2>
        <button type="button" className="btn btn-ghost" onClick={onClose}>
          Back to list
        </button>
      </div>

      {loadError ? (
        <p className="account-banner err">{loadError}</p>
      ) : loading ? (
        <p className="queue-empty">Loading product…</p>
      ) : (
        <form className="product-form" onSubmit={onSubmit}>
          <div className="pf-grid">
            <label className="login-field" htmlFor="pf-name">
              Name
              <input
                id="pf-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
              />
            </label>

            <label className="login-field" htmlFor="pf-namekn">
              Kannada name (optional)
              <input
                id="pf-namekn"
                value={nameKn}
                onChange={(e) => setNameKn(e.target.value)}
                placeholder="ಆಟೋ-ಭರ್ತಿ"
              />
              <span className="field-hint neutral">
                Leave blank to auto-fill by transliteration.
              </span>
            </label>

            <label className="login-field" htmlFor="pf-category">
              Category
              <select
                id="pf-category"
                value={categoryId}
                onChange={(e) =>
                  setCategoryId(
                    e.target.value === '' ? '' : Number(e.target.value),
                  )
                }
                required
              >
                <option value="" disabled>
                  Select a category…
                </option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="login-field" htmlFor="pf-image">
              Image URL (optional)
              <input
                id="pf-image"
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                placeholder="https://…"
              />
              <span className="field-hint neutral">
                A follow-up will add real image upload.
              </span>
            </label>

            <label className="login-field pf-wide" htmlFor="pf-desc">
              Description (optional)
              <textarea
                id="pf-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
              />
            </label>
          </div>

          <div className="pf-toggles">
            <div className="pf-veg">
              <span className="pf-toggle-label">Type</span>
              <div className="pf-seg">
                <button
                  type="button"
                  className={`pf-seg-btn ${vegMarker ? 'active veg' : ''}`}
                  aria-pressed={vegMarker}
                  onClick={() => setVegMarker(true)}
                >
                  Veg
                </button>
                <button
                  type="button"
                  className={`pf-seg-btn ${!vegMarker ? 'active nonveg' : ''}`}
                  aria-pressed={!vegMarker}
                  onClick={() => setVegMarker(false)}
                >
                  Non-veg
                </button>
              </div>
            </div>

            <label className="pf-check">
              <input
                type="checkbox"
                checked={available}
                onChange={(e) => setAvailable(e.target.checked)}
              />
              Available
            </label>

            <label className="pf-check">
              <input
                type="checkbox"
                checked={featured}
                onChange={(e) => setFeatured(e.target.checked)}
              />
              Featured
            </label>
          </div>

          <fieldset className="pf-variants">
            <legend>Variants</legend>
            <p className="field-hint neutral pf-variants-hint">
              At least one variant with a label and a selling price is required.
              Cost price is admin-only (used for margin) and never shown to
              customers.
            </p>

            <div className="pf-variant-list">
              {variants.map((row, idx) => {
                const invalid =
                  !variantRowValid(row) &&
                  (row.label.trim() !== '' ||
                    row.sellingPrice.trim() !== '' ||
                    row.costPrice.trim() !== '');
                return (
                  <div
                    key={idx}
                    className={`pf-variant-row ${invalid ? 'invalid' : ''}`}
                  >
                    <label className="login-field pf-v-label">
                      Label
                      <input
                        value={row.label}
                        onChange={(e) =>
                          updateVariantRow(idx, { label: e.target.value })
                        }
                        placeholder="500 g"
                      />
                    </label>
                    <label className="login-field pf-v-num">
                      Selling ₹
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={row.sellingPrice}
                        onChange={(e) =>
                          updateVariantRow(idx, {
                            sellingPrice: e.target.value,
                          })
                        }
                      />
                    </label>
                    <label className="login-field pf-v-num">
                      Cost ₹
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={row.costPrice}
                        onChange={(e) =>
                          updateVariantRow(idx, { costPrice: e.target.value })
                        }
                      />
                    </label>
                    <label className="login-field pf-v-num">
                      MRP ₹
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={row.mrp}
                        onChange={(e) =>
                          updateVariantRow(idx, { mrp: e.target.value })
                        }
                        placeholder="—"
                      />
                    </label>
                    <label className="login-field pf-v-num">
                      Sort
                      <input
                        type="number"
                        value={row.sortOrder}
                        onChange={(e) =>
                          updateVariantRow(idx, { sortOrder: e.target.value })
                        }
                      />
                    </label>
                    <label className="pf-check pf-v-check">
                      <input
                        type="checkbox"
                        checked={row.available}
                        onChange={(e) =>
                          updateVariantRow(idx, { available: e.target.checked })
                        }
                      />
                      Available
                    </label>
                    <button
                      type="button"
                      className="btn btn-ghost danger pf-v-remove"
                      onClick={() => removeVariantRow(idx)}
                      aria-label="Remove variant"
                    >
                      Remove
                    </button>
                  </div>
                );
              })}
            </div>

            <button
              type="button"
              className="btn btn-ghost"
              onClick={addVariantRow}
            >
              Add variant
            </button>
          </fieldset>

          {error && <p className="account-banner err">{error}</p>}

          <div className="pf-actions">
            <button type="submit" className="btn" disabled={!canSubmit}>
              {busy
                ? 'Saving…'
                : isEdit
                  ? 'Save changes'
                  : 'Create product'}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={busy}
              onClick={onClose}
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
