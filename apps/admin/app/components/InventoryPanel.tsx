'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  correctStock,
  getStockLevels,
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
  newValue: string;
  reason: string;
  saving: boolean;
  error: string | null;
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

  const load = useCallback(
    async (pg: number) => {
      setLoading(true);
      setError(null);
      try {
        const data = await getStockLevels(1, pg, PAGE_SIZE);
        setItems(data.content);
        setTotal(data.totalElements);
      } catch (err) {
        if (err instanceof AuthRequiredError) {
          refreshAuth();
        } else {
          setError('Could not load stock levels.');
        }
      } finally {
        setLoading(false);
      }
    },
    [refreshAuth],
  );

  useEffect(() => {
    void load(page);
  }, [page, load]);

  const filtered = search
    ? items.filter(
        (i) =>
          i.productName.toLowerCase().includes(search.toLowerCase()) ||
          i.variantLabel.toLowerCase().includes(search.toLowerCase()),
      )
    : items;

  const startEdit = (item: StockLevel) => {
    setEdit({
      variantId: item.variantId,
      current: item.onHand,
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
    setEdit((e) => e ? { ...e, saving: true, error: null } : e);
    try {
      await correctStock(edit.variantId, { newOnHand, reason: edit.reason || 'physical count' });
      setEdit(null);
      void load(page);
    } catch {
      setEdit((e) => e ? { ...e, saving: false, error: 'Correction failed. Try again.' } : e);
    }
  };

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <section className="inv-panel">
      <div className="inv-panel-head">
        <h2 className="cat-panel-title">Stock Levels</h2>
        <span className="muted" style={{ fontSize: '0.85rem' }}>{total} variants</span>
      </div>

      <div className="prod-filters">
        <input
          className="prod-search"
          type="search"
          placeholder="Search product or variant…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search stock"
        />
      </div>

      {error && <p className="order-error">{error}</p>}

      {loading && items.length === 0 ? (
        <p className="queue-empty">Loading stock levels…</p>
      ) : filtered.length === 0 ? (
        <p className="queue-empty">No stock levels found.</p>
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
                {filtered.map((item) => {
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
                          // var(--amber) #ef6c00 was ~2.9:1 and failed AA.
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
                              min={0}
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
                            {edit.error && <p className="order-error" style={{ margin: 0 }}>{edit.error}</p>}
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
