'use client';

import { useState } from 'react';
import AnalyticsDashboard from './AnalyticsDashboard';
import Catalogue from './Catalogue';
import InventoryPanel from './InventoryPanel';
import OrderQueue from './OrderQueue';

type Section = 'orders' | 'analytics' | 'inventory' | 'catalogue';

const TABS: { value: Section; label: string }[] = [
  { value: 'orders', label: 'Orders' },
  { value: 'analytics', label: 'Analytics' },
  { value: 'inventory', label: 'Inventory' },
  { value: 'catalogue', label: 'Catalogue' },
];

/**
 * In-page section switcher for the admin surface.
 * Orders: live order queue (SSE, M3).
 * Analytics: GMV dashboard, top products, gross profit, low-stock alerts.
 * Inventory: stock level listing + physical-count corrections.
 * Catalogue: category/product/variant CRUD (M5).
 */
export default function AdminSections() {
  const [section, setSection] = useState<Section>('orders');

  return (
    <>
      <div
        className="queue-tabs section-tabs"
        role="tablist"
        aria-label="Admin section"
      >
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={section === tab.value}
            className={`queue-tab ${section === tab.value ? 'active' : ''}`}
            onClick={() => setSection(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {section === 'orders' && <OrderQueue />}
      {section === 'analytics' && <AnalyticsDashboard />}
      {section === 'inventory' && <InventoryPanel />}
      {section === 'catalogue' && <Catalogue />}
    </>
  );
}
