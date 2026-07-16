'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  getAnalyticsSummary,
  getDailySummary,
  getLowStockItems,
  getTopProducts,
  AuthRequiredError,
} from '@/app/lib/api';
import type { AnalyticsSummary, DailySummary, LowStockItem, TopProduct } from '@/app/lib/types';
import { useAuth } from './AuthProvider';

function fmt(n: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);
}

function pct(num: number, denom: number) {
  if (!denom) return '—';
  return ((num / denom) * 100).toFixed(1) + '%';
}

const PERIOD_OPTIONS = [
  { label: '7 days', days: 7 },
  { label: '30 days', days: 30 },
  { label: '90 days', days: 90 },
];

export default function AnalyticsDashboard() {
  const { refresh: refreshAuth } = useAuth();
  const [period, setPeriod] = useState(30);

  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [daily, setDaily] = useState<DailySummary[]>([]);
  const [topProducts, setTopProducts] = useState<TopProduct[]>([]);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (days: number) => {
      setLoading(true);
      setError(null);
      try {
        const [s, d, tp, ls] = await Promise.all([
          getAnalyticsSummary(),
          getDailySummary(1, days),
          getTopProducts(1, days, 10),
          getLowStockItems(),
        ]);
        setSummary(s);
        setDaily(d);
        setTopProducts(tp);
        setLowStock(ls);
      } catch (err) {
        if (err instanceof AuthRequiredError) {
          refreshAuth();
        } else {
          setError('Could not load analytics. Check the API connection.');
        }
      } finally {
        setLoading(false);
      }
    },
    [refreshAuth],
  );

  useEffect(() => {
    void load(period);
  }, [period, load]);

  const maxRevenue = daily.length ? Math.max(...daily.map((d) => d.revenue), 1) : 1;
  // Period totals derived from the daily series (already carries per-day COGS).
  const periodRevenue = daily.reduce((sum, d) => sum + d.revenue, 0);
  const periodGrossProfit = daily.reduce((sum, d) => sum + d.grossProfit, 0);

  return (
    <section className="analytics">
      {/* Period selector — aria-pressed toggles, not half-implemented ARIA tabs. */}
      <div className="queue-tabs" aria-label="Analytics period">
        {PERIOD_OPTIONS.map((opt) => (
          <button
            key={opt.days}
            type="button"
            aria-pressed={period === opt.days}
            className={`queue-tab ${period === opt.days ? 'active' : ''}`}
            onClick={() => setPeriod(opt.days)}
          >
            {opt.label}
          </button>
        ))}
        {loading && <span className="muted" style={{ marginLeft: 'auto', fontSize: '0.82rem' }}>Loading…</span>}
      </div>

      {error && <p className="order-error">{error}</p>}

      {/* KPI cards */}
      {summary && (
        <div className="kpi-grid">
          <div className="kpi-card">
            <div className="kpi-label">Today&apos;s Revenue</div>
            <div className="kpi-value">{fmt(summary.todayRevenue)}</div>
            <div className="kpi-sub">{summary.todayOrders} orders placed</div>
          </div>
          <div className="kpi-card">
            <div className="kpi-label">Delivered Today</div>
            <div className="kpi-value">{summary.todayDelivered}</div>
            <div className="kpi-sub">of {summary.todayOrders} placed</div>
          </div>
          <div className="kpi-card kpi-card-warn" style={{ borderLeftColor: summary.pendingOrders > 0 ? 'var(--amber)' : 'var(--border)' }}>
            <div className="kpi-label">Pending Queue</div>
            <div className="kpi-value">{summary.pendingOrders}</div>
            <div className="kpi-sub">active orders</div>
          </div>
          <div className="kpi-card">
            <div className="kpi-label">Week Revenue</div>
            <div className="kpi-value">{fmt(summary.weekRevenue)}</div>
            <div className="kpi-sub">{summary.weekOrders} orders (7 days)</div>
          </div>
          <div className="kpi-card kpi-card-profit">
            <div className="kpi-label">Gross Profit ({period}d)</div>
            <div className="kpi-value">{fmt(periodGrossProfit)}</div>
            <div className="kpi-sub">{pct(periodGrossProfit, periodRevenue)} margin · {fmt(periodRevenue)} revenue</div>
          </div>
        </div>
      )}

      {/* Daily revenue chart — full width */}
      <div className="analytics-card">
        <h2 className="analytics-card-title">Revenue — last {period} days</h2>
        {daily.length === 0 && !loading ? (
          <p className="muted" style={{ textAlign: 'center', padding: '1.5rem 0' }}>No orders in this period.</p>
        ) : (
          <>
            {/* Bars are focusable buttons: values reachable by keyboard and
                announced by screen readers (title alone is mouse-only). */}
            <div
              className="bar-chart"
              aria-label={`Daily revenue, last ${period} days: total ${fmt(periodRevenue)}, gross profit ${fmt(periodGrossProfit)}. Values per day follow.`}
            >
              {[...daily].reverse().map((d) => {
                const heightPct = (d.revenue / maxRevenue) * 100;
                const gpPct = pct(d.grossProfit, d.revenue);
                const detail = `${d.date}: revenue ${fmt(d.revenue)}, ${d.orders} orders, gross profit ${fmt(d.grossProfit)} (${gpPct})`;
                return (
                  <button
                    key={d.date}
                    type="button"
                    className="bar-col"
                    title={detail}
                    aria-label={detail}
                  >
                    <div className="bar-fill" style={{ height: `${Math.max(heightPct, 2)}%` }} />
                    <div className="bar-label" aria-hidden>{d.date.slice(5)}</div>
                  </button>
                );
              })}
            </div>
            {/* Screen-reader table alternative for the chart data. */}
            <table className="sr-only">
              <caption>Daily revenue, orders and gross profit — last {period} days</caption>
              <thead>
                <tr><th>Date</th><th>Revenue</th><th>Orders</th><th>Gross profit</th></tr>
              </thead>
              <tbody>
                {daily.map((d) => (
                  <tr key={d.date}>
                    <td>{d.date}</td>
                    <td>{fmt(d.revenue)}</td>
                    <td>{d.orders}</td>
                    <td>{fmt(d.grossProfit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>

      {/* Two equal columns: Top Products + Low Stock */}
      <div className="analytics-row">
        {/* Top products */}
        <div className="analytics-card">
          <h2 className="analytics-card-title">Top Products</h2>
          {topProducts.length === 0 && !loading ? (
            <p className="muted" style={{ textAlign: 'center', padding: '1rem 0' }}>No sales data.</p>
          ) : (
            <table className="analytics-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Qty</th>
                  <th className="num">Revenue</th>
                </tr>
              </thead>
              <tbody>
                {topProducts.map((p, i) => (
                  <tr key={i}>
                    <td>
                      <span className="tp-name">{p.productName}</span>
                      <span className="tp-label">{p.variantLabel}</span>
                    </td>
                    <td className="num">{p.totalQty}</td>
                    <td className="num">{fmt(p.totalRevenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Low stock alerts */}
        <div className="analytics-card">
          <h2
            className="analytics-card-title"
            style={{ color: lowStock.length > 0 ? 'var(--danger)' : undefined }}
          >
            Low Stock Alerts{lowStock.length > 0 ? ` (${lowStock.length})` : ''}
          </h2>
          {lowStock.length === 0 ? (
            <p className="muted" style={{ textAlign: 'center', padding: '1rem 0' }}>
              {loading ? 'Loading…' : 'All items above their threshold.'}
            </p>
          ) : (
            <table className="analytics-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Available</th>
                  <th className="num">Threshold</th>
                </tr>
              </thead>
              <tbody>
                {lowStock.map((item) => (
                  <tr key={item.variantId} className={item.available <= 0 ? 'row-critical' : 'row-warn'}>
                    <td>
                      <span className="tp-name">{item.productName}</span>
                      <span className="tp-label">{item.variantLabel}</span>
                    </td>
                    <td className="num">{item.available <= 0 ? <strong style={{ color: 'var(--danger)' }}>OUT</strong> : item.available}</td>
                    <td className="num">{item.threshold}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}
