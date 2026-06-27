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

  return (
    <section className="analytics">
      {/* Period selector */}
      <div className="queue-tabs" role="tablist" aria-label="Analytics period">
        {PERIOD_OPTIONS.map((opt) => (
          <button
            key={opt.days}
            type="button"
            role="tab"
            aria-selected={period === opt.days}
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
        </div>
      )}

      <div className="analytics-row">
        {/* Daily revenue chart */}
        <div className="analytics-card analytics-card-wide">
          <h3 className="analytics-card-title">Revenue — last {period} days</h3>
          {daily.length === 0 && !loading ? (
            <p className="muted" style={{ textAlign: 'center', padding: '1.5rem 0' }}>No orders in this period.</p>
          ) : (
            <div className="bar-chart" role="img" aria-label={`Revenue bar chart, last ${period} days`}>
              {[...daily].reverse().map((d) => {
                const heightPct = (d.revenue / maxRevenue) * 100;
                const gpPct = pct(d.grossProfit, d.revenue);
                return (
                  <div key={d.date} className="bar-col" title={`${d.date}\nRevenue: ${fmt(d.revenue)}\nOrders: ${d.orders}\nGross profit: ${fmt(d.grossProfit)} (${gpPct})`}>
                    <div className="bar-fill" style={{ height: `${Math.max(heightPct, 2)}%` }} />
                    <div className="bar-label">{d.date.slice(5)}</div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Top products */}
        <div className="analytics-card">
          <h3 className="analytics-card-title">Top Products</h3>
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
      </div>

      {/* Low stock alerts */}
      {lowStock.length > 0 && (
        <div className="analytics-card">
          <h3 className="analytics-card-title" style={{ color: 'var(--danger)' }}>
            Low Stock Alerts ({lowStock.length})
          </h3>
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
        </div>
      )}
    </section>
  );
}
