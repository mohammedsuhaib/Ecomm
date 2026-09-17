'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ApiError,
  AuthRequiredError,
  deleteProduct,
  getAdminProducts,
  importProductsCsv,
  setProductAvailability,
} from '@/app/lib/api';
import { formatRupees } from '@/app/lib/format';
import type { AdminProduct, Category, ProductImportResult } from '@/app/lib/types';
import ProductForm from './ProductForm';
import { ListSkeleton } from './Skeleton';

const PAGE_SIZE = 50;

// Downloadable starter file for the CSV import (one row per variant).
const CSV_TEMPLATE =
  'name,category,variant_label,selling_price,cost_price,mrp,name_kn,description,veg,hsn,gst_rate,image_url\n' +
  'Amul Butter,dairy,100 g,62,55,65,,Salted butter,Y,0405,5,\n' +
  'Amul Butter,dairy,500 g,295,270,305,,Salted butter,Y,0405,5,\n';

/** Min–max selling price across variants, e.g. "₹40 – ₹120", or "—" if none. */
function priceRange(p: AdminProduct): string {
  const prices = p.variants.map((v) => v.sellingPrice);
  if (prices.length === 0) return '—';
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max
    ? formatRupees(min)
    : `${formatRupees(min)} – ${formatRupees(max)}`;
}

/**
 * Products panel: a search box (q) + category filter + pagination over the
 * admin product list (which includes unavailable items). Each row shows the
 * name (+ Kannada name), category, price range and badges; row actions are
 * Edit, Activate/Deactivate (availability toggle) and a guarded Delete. The
 * create/edit form mounts inline above the list.
 */
export default function ProductsPanel({
  categories,
  onAuthExpired,
}: {
  categories: Category[];
  onAuthExpired: () => void;
}) {
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rowBusyId, setRowBusyId] = useState<number | null>(null);

  // Editing state: 'new' for create, a product id for edit, null for the list.
  const [editing, setEditing] = useState<'new' | number | null>(null);

  // CSV import: pick file -> dry-run preview -> confirm -> result.
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [importPreview, setImportPreview] =
    useState<{ fileName: string; csv: string; result: ProductImportResult } | null>(null);
  const [importDone, setImportDone] = useState<ProductImportResult | null>(null);
  const [importBusy, setImportBusy] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);

  async function onImportFile(file: File) {
    setImportError(null);
    setImportDone(null);
    setImportPreview(null);
    setImportBusy(true);
    try {
      const csv = await file.text();
      const result = await importProductsCsv(csv, true); // dry-run preview
      setImportPreview({ fileName: file.name, csv, result });
    } catch (err) {
      if (err instanceof AuthRequiredError) onAuthExpired();
      else if (err instanceof ApiError) setImportError(err.message);
      else setImportError('Could not read that file.');
    } finally {
      setImportBusy(false);
    }
  }

  async function onConfirmImport() {
    if (!importPreview) return;
    setImportBusy(true);
    setImportError(null);
    try {
      const result = await importProductsCsv(importPreview.csv, false);
      setImportDone(result);
      setImportPreview(null);
      await load();
    } catch (err) {
      if (err instanceof AuthRequiredError) onAuthExpired();
      else setImportError('Import failed. Nothing may have been written — retry.');
    } finally {
      setImportBusy(false);
    }
  }

  // Debounce the search box so each keystroke doesn't fire a request.
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Monotonic request id: results from a superseded (slower) request are dropped
  // so a stale response can't overwrite a newer one (e.g. "ab" landing after "abc").
  const reqSeq = useRef(0);

  const load = useCallback(async () => {
    const seq = ++reqSeq.current;
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminProducts({
        categoryId: categoryId === '' ? undefined : categoryId,
        q: q.trim() || undefined,
        page,
        size: PAGE_SIZE,
      });
      if (seq !== reqSeq.current) return; // a newer request superseded this one
      setProducts(data.content);
      setTotal(data.totalElements);
      // Deleting the last row on a non-first page strands an empty page — step back.
      if (data.content.length === 0 && page > 0) {
        setPage((p) => Math.max(0, p - 1));
      }
    } catch (err) {
      if (seq !== reqSeq.current) return;
      if (err instanceof AuthRequiredError) {
        onAuthExpired();
      } else {
        setError('Could not load products. Check the connection and retry.');
      }
    } finally {
      if (seq === reqSeq.current) setLoading(false);
    }
  }, [categoryId, q, page, onAuthExpired]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      void load();
    }, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [load]);

  // Reset to the first page whenever the filters change.
  function onFilterChange(next: () => void) {
    setPage(0);
    next();
  }

  async function onToggleAvailability(p: AdminProduct) {
    setRowBusyId(p.id);
    setError(null);
    try {
      await setProductAvailability(p.id, !p.available);
      await load();
    } catch (err) {
      if (err instanceof AuthRequiredError) onAuthExpired();
      else setError('Could not update availability. Please try again.');
    } finally {
      setRowBusyId(null);
    }
  }

  async function onDelete(p: AdminProduct) {
    if (
      !window.confirm(
        `Delete “${p.name}” permanently? This removes the product and all its variants and can’t be undone.`,
      )
    ) {
      return;
    }
    setRowBusyId(p.id);
    setError(null);
    try {
      await deleteProduct(p.id);
      await load();
    } catch (err) {
      if (err instanceof AuthRequiredError) onAuthExpired();
      else if (err instanceof ApiError && err.status === 422) {
        setError('This product can’t be deleted right now (it may be in use).');
      } else {
        setError('Could not delete the product. Please try again.');
      }
    } finally {
      setRowBusyId(null);
    }
  }

  if (editing !== null) {
    return (
      <ProductForm
        productId={editing === 'new' ? null : editing}
        categories={categories}
        onAuthExpired={onAuthExpired}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await load();
        }}
      />
    );
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <section className="prod-panel">
      <div className="cat-panel-head">
        <h2 className="cat-panel-title">Products</h2>
        <div className="pf-actions">
          <a
            className="link-action"
            style={{ alignSelf: 'center', fontSize: '0.82rem' }}
            href={`data:text/csv;charset=utf-8,${encodeURIComponent(CSV_TEMPLATE)}`}
            download="products-template.csv"
          >
            Download template
          </a>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            hidden
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = ''; // allow re-picking the same file
              if (file) void onImportFile(file);
            }}
          />
          <button
            type="button"
            className="btn btn-ghost"
            disabled={importBusy || categories.length === 0}
            onClick={() => fileInputRef.current?.click()}
          >
            {importBusy ? 'Checking…' : 'Import CSV'}
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => setEditing('new')}
            disabled={categories.length === 0}
            title={
              categories.length === 0
                ? 'Add a category first'
                : undefined
            }
          >
            Add product
          </button>
        </div>
      </div>

      {importError && <p className="order-error">{importError}</p>}
      {importPreview && (
        <div className="import-box">
          <p>
            <strong>{importPreview.fileName}</strong>: will create{' '}
            {importPreview.result.created}, skip {importPreview.result.skipped}{' '}
            already-existing, {importPreview.result.errors.length} error
            {importPreview.result.errors.length === 1 ? '' : 's'}. Nothing has
            been written yet.
          </p>
          {importPreview.result.errors.length > 0 && (
            <ul className="import-errors">
              {importPreview.result.errors.map((e) => (
                <li key={`${e.row}-${e.message}`}>
                  Line {e.row}: {e.message}
                </li>
              ))}
            </ul>
          )}
          <div className="pf-actions">
            <button
              type="button"
              className="btn"
              disabled={importBusy || importPreview.result.created === 0}
              onClick={() => void onConfirmImport()}
            >
              {importBusy
                ? 'Importing…'
                : `Import ${importPreview.result.created} product${importPreview.result.created === 1 ? '' : 's'}`}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={importBusy}
              onClick={() => setImportPreview(null)}
            >
              Cancel
            </button>
          </div>
        </div>
      )}
      {importDone && (
        <p className="account-banner ok">
          Imported {importDone.created} product
          {importDone.created === 1 ? '' : 's'} ({importDone.skipped} skipped
          {importDone.errors.length > 0
            ? `, ${importDone.errors.length} errors — fix and re-run; existing products are skipped`
            : ''}
          ).
        </p>
      )}

      <div className="prod-filters">
        <input
          className="prod-search"
          type="search"
          value={q}
          onChange={(e) => onFilterChange(() => setQ(e.target.value))}
          placeholder="Search products…"
          aria-label="Search products"
        />
        <select
          className="prod-filter-select"
          value={categoryId}
          onChange={(e) =>
            onFilterChange(() =>
              setCategoryId(e.target.value === '' ? '' : Number(e.target.value)),
            )
          }
          aria-label="Filter by category"
        >
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {error && <p className="account-banner err">{error}</p>}

      {loading && products.length === 0 ? (
        <ListSkeleton label="Loading products…" rows={6} />
      ) : products.length === 0 ? (
        <p className="queue-empty">No products match these filters.</p>
      ) : (
        <div className="prod-table-wrap">
          <table className="prod-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Category</th>
                <th className="num">Price</th>
                <th className="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id} className={p.available ? '' : 'is-unavailable'}>
                  <td>
                    <div className="prod-name-cell">
                      <span className="prod-name">
                        {/* Both states, always: rendering the dot only for veg
                            made non-veg products indistinguishable from ones
                            whose marker had never been set, which is exactly
                            what staff use this column to check. */}
                        <span
                          className={`veg-dot ${p.vegMarker ? 'veg' : 'nonveg'}`}
                          role="img"
                          aria-label={p.vegMarker ? 'Vegetarian' : 'Non-vegetarian'}
                          title={p.vegMarker ? 'Veg' : 'Non-veg'}
                        >
                          ●
                        </span>
                        {p.name}
                      </span>
                      {p.nameKn && (
                        <span className="prod-name-kn muted">{p.nameKn}</span>
                      )}
                      <span className="prod-badges">
                        {!p.available && (
                          <span className="badge badge-off">Unavailable</span>
                        )}
                        {p.featured && (
                          <span className="badge badge-featured">Featured</span>
                        )}
                      </span>
                    </div>
                  </td>
                  <td>{p.categoryName}</td>
                  <td className="num">{priceRange(p)}</td>
                  <td className="actions-col">
                    <div className="prod-row-actions">
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={rowBusyId === p.id}
                        onClick={() => setEditing(p.id)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={rowBusyId === p.id}
                        onClick={() => onToggleAvailability(p)}
                      >
                        {rowBusyId === p.id
                          ? '…'
                          : p.available
                            ? 'Deactivate'
                            : 'Activate'}
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost danger"
                        disabled={rowBusyId === p.id}
                        onClick={() => onDelete(p)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {total > PAGE_SIZE && (
        <div className="prod-pager">
          <button
            type="button"
            className="btn btn-ghost"
            disabled={page === 0 || loading}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </button>
          <span className="muted prod-pager-status">
            Page{' '}
            {/* A selector, not just a counter: stepping through a long
                catalogue one Next at a time was the complaint. A plain select
                is right at this scale — one supermarket's catalogue is tens of
                pages at most, and it works with a thumb on a tablet. */}
            <select
              className="prod-pager-select"
              value={page}
              disabled={loading}
              onChange={(e) => setPage(Number(e.target.value))}
              aria-label="Go to page"
            >
              {Array.from({ length: totalPages }, (_, i) => (
                <option key={i} value={i}>
                  {i + 1}
                </option>
              ))}
            </select>{' '}
            of {totalPages} · {total} products
          </span>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}
