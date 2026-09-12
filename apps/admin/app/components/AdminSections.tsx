'use client';

import { useEffect, useState } from 'react';
import AnalyticsDashboard from './AnalyticsDashboard';
import Catalogue from './Catalogue';
import InventoryPanel from './InventoryPanel';
import OrderQueue from './OrderQueue';
import RidersPanel from './RidersPanel';
import StorePanel from './StorePanel';
import StaffPanel from './StaffPanel';
import { useAuth } from './AuthProvider';

type Section = 'orders' | 'analytics' | 'inventory' | 'catalogue' | 'riders' | 'store' | 'staff';

const TABS: { value: Section; label: string }[] = [
  { value: 'orders', label: 'Orders' },
  { value: 'analytics', label: 'Analytics' },
  { value: 'inventory', label: 'Inventory' },
  { value: 'catalogue', label: 'Catalogue' },
  { value: 'riders', label: 'Riders' },
  { value: 'store', label: 'Store' },
  // ADMIN only — filtered out of the tab strip for store staff (see below).
  { value: 'staff', label: 'Staff' },
];

const isSection = (v: string | null): v is Section =>
  v != null && TABS.some((t) => t.value === v);

/**
 * In-page section switcher for the admin surface.
 * Orders: live order queue (SSE, M3).
 * Analytics: GMV dashboard, top products, gross profit, low-stock alerts.
 * Inventory: stock level listing + physical-count corrections.
 * Catalogue: category/product/variant CRUD (M5).
 *
 * The active section is mirrored into ?tab= (history.replaceState — no
 * Next.js navigation) so a refresh or shared link restores it. Toggle
 * buttons use aria-pressed rather than a half-implemented ARIA tabs
 * pattern (no arrow-key roving / tabpanel wiring), which would misreport
 * semantics to screen readers.
 */
export default function AdminSections() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  // The staff directory (and the ability to reset staff passwords) is admin
  // only; the API enforces it too — this just keeps the tab out of the way.
  const tabs = TABS.filter((t) => t.value !== 'staff' || isAdmin);
  const [section, setSection] = useState<Section>('orders');

  // Restore section from the URL on first mount.
  useEffect(() => {
    const fromUrl = new URLSearchParams(window.location.search).get('tab');
    if (isSection(fromUrl)) setSection(fromUrl);
  }, []);

  const select = (next: Section) => {
    setSection(next);
    const url = new URL(window.location.href);
    if (next === 'orders') url.searchParams.delete('tab');
    else url.searchParams.set('tab', next);
    window.history.replaceState(null, '', url);
  };

  return (
    <>
      <div className="queue-tabs section-tabs" aria-label="Admin section">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            aria-pressed={section === tab.value}
            className={`queue-tab ${section === tab.value ? 'active' : ''}`}
            onClick={() => select(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {section === 'orders' && <OrderQueue />}
      {section === 'analytics' && <AnalyticsDashboard />}
      {section === 'inventory' && <InventoryPanel />}
      {section === 'catalogue' && <Catalogue />}
      {section === 'riders' && <RidersPanel />}
      {section === 'store' && <StorePanel />}
      {section === 'staff' && isAdmin && <StaffPanel />}
    </>
  );
}
