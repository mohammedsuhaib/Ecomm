'use client';

import { useCallback, useEffect, useState } from 'react';
import { AuthRequiredError, getAdminCategories } from '@/app/lib/api';
import type { Category } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import CategoriesPanel from './CategoriesPanel';
import ProductsPanel from './ProductsPanel';

/**
 * Catalogue-management surface (M5). Owns the category list so the categories
 * panel (CRUD) and the products panel (filter dropdown + product-form category
 * picker) stay in sync — editing a category here immediately re-labels product
 * filters and the create/edit form. A 401 from any catalogue call clears the
 * session (api.ts) and bubbles up as AuthRequiredError; we re-sync auth so the
 * LoginGate drops to the sign-in form.
 */
export default function Catalogue() {
  const { refresh: refreshAuth } = useAuth();
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const onAuthExpired = useCallback(() => {
    refreshAuth();
  }, [refreshAuth]);

  const loadCategories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await getAdminCategories();
      setCategories(list);
    } catch (err) {
      if (err instanceof AuthRequiredError) {
        onAuthExpired();
      } else {
        setError('Could not load categories. Check the connection and retry.');
      }
    } finally {
      setLoading(false);
    }
  }, [onAuthExpired]);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  return (
    <section className="catalogue">
      {error && <p className="order-error queue-error">{error}</p>}

      <CategoriesPanel
        categories={categories}
        loading={loading}
        onChanged={loadCategories}
        onAuthExpired={onAuthExpired}
      />

      <ProductsPanel
        categories={categories}
        onAuthExpired={onAuthExpired}
      />
    </section>
  );
}
