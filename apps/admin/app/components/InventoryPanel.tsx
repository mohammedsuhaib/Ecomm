'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ApiError,
  correctStock,
  getStockLevels,
  serverMessage,
  AuthRequiredError,
} from '@/app/lib/api';
import type { StockLevel } from '@/app/lib/types';
import { useAuth } from './AuthProvider';

function fmt(n: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(n);
}

interface EditState {
  variantId: number;
  current: number;
  /** Units already committed to open orders; a count may not go below this. */
  reserved: number;
  newValue: string;
  reason: string;
  saving: boolean;
  error: string | null;
}

/**
 * What to tell the operator when a correction is refused.
 *
 * The old text was "Correction failed. Try again." for every failure, which hid
 * the one thing that mattered: a count below the units reserved for open orders
 * is refused by rule, so trying again with the same number can only fail again.
 * Prefer the server's message, and only fall back to generic advice when there
 * genuinely isn't one.
 */
function correctionError(err: unknown): string {
  if (err instanceof AuthRequiredError) {
    return 'Session expired — please log in again.';
  }
  if (err instanceof ApiError && err.status === 0) {
    return 'Could not reach the server. Check your connection.';
  }
  return (
    serverMessage(err) ?? 'Could not save the correction. Please try again.'
  );
}

export default function InventoryPanel() {
  const { refresh: refreshAuth } = useAuth();
  const [items, setItems] = useState<StockLevel[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [edit, setEdit] = useState<EditState | null>(null);
  const PAGE_SIZE = 100;

  // Monotonic request id: a slower response from a superseded keystroke must
  // not overwrite a newer one (e.g. "hor" landing after "horlicks").
  const reqSeq = useRef(0);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(
    async (pg: number, term: string) => {
      const seq = ++reqSeq.current;
      setLoading(true);
      setError(null);
      try {
        const data = await getStockLevels(1, pg, PAGE_SIZE, term.trim() || undefined);
        if (seq !== reqSeq.current) return; // superseded
        setItems(data.content);
        setTotal(data.totalElements);
        // A correction can move a row into another page's bucket and strand an
        // empty page — step back rather than showing "no stock levels found".
        if (data.content.length === 0 && pg > 0) {
          setPage((p) => Math.max(0, p - 1));
        }
      } catch (err) {
        if (seq !== reqSeq.current) return;
        if (err instanceof AuthRequiredError) {
          refreshAuth();
        } else {
          setError('Could not load stock levels.');
        }
      } finally {
        if (seq === reqSeq.current) setLoading(false);
      }
    },
    [refreshAuth],
  );

  // Debounce so each keystroke doesn't fire a request.
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      void load(page, search);
    }, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [page, search, load]);

  const startEdit = (item: StockLevel) => {
    setEdit({
      variantId: item.variantId,
      current: item.onHand,
      reserved: item.reserved,
      newValue: String(item.onHand),
      reason: '',
      saving: false,
      error: null,
    });
  };

  const saveCorrection = async () => {
    if (!edit) return;
    const newOnHand = parseInt(edit.newValue, 10);
    if (isNaN(newOnHand) || newOnHand < 0) {
      setEdit((e) => e ? { ...e, error: 'Enter a valid non-negative number.' } : e);
      return;
    }
    // Say it before the round-trip: the server enforces this, but a refusal the
    // operator could have been warned about is a wasted attempt.
    if (newOnHand < edit.reserved) {
      setEdit((e) => e ? {
        ...e,
        error: `${edit.reserved} unit(s) are reserved for open orders, so the count `
          + `can't go below ${edit.reserved}. Cancel or fulfil those orders first.`,
      } : e);
      return;
    }
    setEdit((e) => e ? { ...e, saving: true, error: null } : e);
    try {
      await correctStock(edit.variantId, { newOnHand, reason: edit.reason || 'physical count' });
      setEdit(null);
      void load(page, search);
    } catch (err) {
      const message = correctionError(err);
      setEdit((e) => e ? { ...e, saving: false, error: message } : e);
      if (err instanceof AuthRequiredError) void refreshAuth();
    }
  };

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <section className="inv-panel">
      <div className="inv-panel-head">
        <h2 className="cat-panel-title">Stock Levels</h2>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          {search ? `${total} matching` : `${total} variants`}
        </span>
      </div>

      <div className="prod-filters">
        <input
          className="prod-search"
          type="search"
          placeholder="Search product or variant…"
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
          aria-label="Search stock"
        />
      </div>

      {error && <p className="order-error">{error}</p>}

      {loading && items.length === 0 ? (
        <p className="queue-empty">Loading stock levels…</p>
      ) : items.length === 0 ? (
        <p className="queue-empty">
          {search ? `No stock matches "${search}".` : 'No stock levels found.'}
        </p>
      ) : (
        <>
          <div className="prod-table-wrap">
            <table className="prod-table">
              <thead>
                <tr>
                  <th>Product / Variant</th>
                  <th className="num">Price</th>
                  <th className="num">On Hand</th>
                  <th className="num">Reserved</th>
                  <th className="num">Available</th>
                  <th className="num">Threshold</th>
                  <th className="actions-col">Action</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const isLow = item.available <= item.lowStockThreshold;
                  const isOut = item.available <= 0;
                  return (
                    <tr key={item.id} className={isOut ? 'inv-row-out' : isLow ? 'inv-row-low' : ''}>
                      <td>
                        <div className="prod-name-cell">
                          <span className="prod-name">{item.productName}</span>
                          <span className="prod-name-kn muted">{item.variantLabel}</span>
                        </div>
                      </td>
                      <td className="num">{fmt(item.sellingPrice)}</td>
                      <td className="num">{item.onHand}</td>
                      <td className="num">{item.reserved}</td>
                      <td className="num">
                        {isOut ? (
                          <strong style={{ color: 'var(--danger)' }}>0</strong>
                        ) : isLow ? (
                          // #b34700 is 5.2:1 on the .inv-row-low bg (#fff7e6);
                          // a lighter amber ink here fails AA (~2.9:1); keep the dark warning tone.
                          <span style={{ color: '#b34700', fontWeight: 700 }}>{item.available}</span>
                        ) : (
                          item.available
                        )}
                      </td>
                      <td className="num">{item.lowStockThreshold}</td>
                      <td className="actions-col">
                        {edit?.variantId === item.variantId ? (
                          <div className="inv-edit-inline">
                            <input
                              type="number"
                              // Reserved units are physically committed to open
                              // orders, so they are the real floor — not 0.
                              min={edit.reserved}
                              title={
                                edit.reserved > 0
                                  ? `At least ${edit.reserved} — that many are reserved for open orders`
                                  : undefined
                              }
                              className="inv-count-input"
                              value={edit.newValue}
                              onChange={(e) =>
                                setEdit((s) => s ? { ...s, newValue: e.target.value } : s)
                              }
                              aria-label="New on-hand count"
                            />
                            <input
                              type="text"
                              className="inv-reason-input"
                              placeholder="Reason (optional)"
                              value={edit.reason}
                              onChange={(e) =>
                                setEdit((s) => s ? { ...s, reason: e.target.value } : s)
                              }
                              aria-label="Correction reason"
                            />
                            {edit.error && (
                              <p className="order-error inv-edit-error" role="alert">
                                {edit.error}
                              </p>
                            )}
                            <div className="inv-edit-actions">
                              <button
                                type="button"
                                className="btn"
                                style={{ fontSize: '0.82rem', padding: '0.3rem 0.7rem' }}
                                onClick={saveCorrection}
                                disabled={edit.saving}
                              >
                                {edit.saving ? 'Saving…' : 'Save'}
                              </button>
                              <button
                                type="button"
                                className="btn btn-ghost"
                                style={{ fontSize: '0.82rem', padding: '0.3rem 0.7rem' }}
                                onClick={() => setEdit(null)}
                                disabled={edit.saving}
                              >
                                Cancel
                              </button>
                            </div>
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="btn btn-ghost"
                            style={{ fontSize: '0.82rem', padding: '0.3rem 0.7rem' }}
                            onClick={() => startEdit(item)}
                          >
                            Correct
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="prod-pager">
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                disabled={page === 0 || loading}
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={page >= totalPages - 1 || loading}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
