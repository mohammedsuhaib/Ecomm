'use client';

import { useState } from 'react';
import Catalogue from './Catalogue';
import OrderQueue from './OrderQueue';

type Section = 'orders' | 'catalogue';

/**
 * In-page section switcher for the admin surface. Reuses the queue's pill-tab
 * styling for an Orders | Catalogue toggle, defaulting to Orders so the live
 * queue (M3) keeps its place. Both sections stay mounted-on-demand (only the
 * active one renders), so the order queue's SSE subscription is torn down when
 * staff move to the catalogue and re-established on return.
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
        <button
          type="button"
          role="tab"
          aria-selected={section === 'orders'}
          className={`queue-tab ${section === 'orders' ? 'active' : ''}`}
          onClick={() => setSection('orders')}
        >
          Orders
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={section === 'catalogue'}
          className={`queue-tab ${section === 'catalogue' ? 'active' : ''}`}
          onClick={() => setSection('catalogue')}
        >
          Catalogue
        </button>
      </div>

      {section === 'orders' ? <OrderQueue /> : <Catalogue />}
    </>
  );
}
