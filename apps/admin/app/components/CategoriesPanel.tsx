'use client';

import { useState, type FormEvent } from 'react';
import {
  ApiError,
  AuthRequiredError,
  createCategory,
  deleteCategory,
  updateCategory,
} from '@/app/lib/api';
import type { Category } from '@/app/lib/types';

/**
 * Categories panel: lists categories (name / sort order / image), supports
 * add, inline edit (rename, re-sort, re-image) and delete. Deleting a category
 * that still has products returns 422 from the backend — we surface that as a
 * clear "move or remove its products first" message rather than a generic error.
 * Mirrors ChangePassword's busy / error / success pattern.
 */
export default function CategoriesPanel({
  categories,
  loading,
  onChanged,
  onAuthExpired,
}: {
  categories: Category[];
  loading: boolean;
  onChanged: () => Promise<void> | void;
  onAuthExpired: () => void;
}) {
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function mapError(err: unknown, action: 'delete' | 'save'): string {
    if (err instanceof AuthRequiredError) return 'Session expired — please log in again.';
    if (err instanceof ApiError) {
      if (action === 'delete' && err.status === 422) {
        return 'This category still has products. Move or remove them before deleting it.';
      }
      if (err.status === 409) {
        return 'A category with that name or slug already exists.';
      }
      if (err.status === 0) return 'Could not reach the server. Check your connection.';
    }
    return action === 'delete'
      ? 'Could not delete the category. Please try again.'
      : 'Could not save the category. Please try again.';
  }

  async function handleAuth(err: unknown) {
    if (err instanceof AuthRequiredError) onAuthExpired();
  }

  async function onDelete(cat: Category) {
    if (
      !window.confirm(
        `Delete category “${cat.name}”? This can’t be undone.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deleteCategory(cat.id);
      await onChanged();
    } catch (err) {
      setError(mapError(err, 'delete'));
      await handleAuth(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="cat-panel">
      <div className="cat-panel-head">
        <h2 className="cat-panel-title">Categories</h2>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => {
            setAdding((a) => !a);
            setEditingId(null);
            setError(null);
          }}
        >
          {adding ? 'Cancel' : 'Add category'}
        </button>
      </div>

      {error && <p className="account-banner err">{error}</p>}

      {adding && (
        <CategoryForm
          mode="create"
          busy={busy}
          onCancel={() => setAdding(false)}
          onSubmit={async (payload) => {
            setBusy(true);
            setError(null);
            try {
              await createCategory({
                name: payload.name,
                slug: payload.slug || undefined,
                sortOrder: payload.sortOrder,
                imageUrl: payload.imageUrl || null,
              });
              setAdding(false);
              await onChanged();
            } catch (err) {
              setError(mapError(err, 'save'));
              await handleAuth(err);
            } finally {
              setBusy(false);
            }
          }}
        />
      )}

      {loading && categories.length === 0 ? (
        <p className="queue-empty">Loading categories…</p>
      ) : categories.length === 0 ? (
        <p className="queue-empty">No categories yet. Add one to get started.</p>
      ) : (
        <ul className="cat-list">
          {categories.map((cat) =>
            editingId === cat.id ? (
              <li key={cat.id} className="cat-row editing">
                <CategoryForm
                  mode="edit"
                  initial={cat}
                  busy={busy}
                  onCancel={() => setEditingId(null)}
                  onSubmit={async (payload) => {
                    setBusy(true);
                    setError(null);
                    try {
                      await updateCategory(cat.id, {
                        name: payload.name,
                        sortOrder: payload.sortOrder,
                        imageUrl: payload.imageUrl || null,
                      });
                      setEditingId(null);
                      await onChanged();
                    } catch (err) {
                      setError(mapError(err, 'save'));
                      await handleAuth(err);
                    } finally {
                      setBusy(false);
                    }
                  }}
                />
              </li>
            ) : (
              <li key={cat.id} className="cat-row">
                <div className="cat-row-main">
                  <span className="cat-name">{cat.name}</span>
                  <span className="cat-meta muted">
                    sort {cat.sortOrder} · /{cat.slug}
                  </span>
                </div>
                <div className="cat-row-actions">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={busy}
                    onClick={() => {
                      setEditingId(cat.id);
                      setAdding(false);
                      setError(null);
                    }}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost danger"
                    disabled={busy}
                    onClick={() => onDelete(cat)}
                  >
                    Delete
                  </button>
                </div>
              </li>
            ),
          )}
        </ul>
      )}
    </section>
  );
}

interface CategoryFormValues {
  name: string;
  slug: string;
  sortOrder: number | undefined;
  imageUrl: string;
}

/** Add/edit form for a single category. Slug is only editable on create. */
function CategoryForm({
  mode,
  initial,
  busy,
  onSubmit,
  onCancel,
}: {
  mode: 'create' | 'edit';
  initial?: Category;
  busy: boolean;
  onSubmit: (values: CategoryFormValues) => Promise<void> | void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [slug, setSlug] = useState(initial?.slug ?? '');
  const [sortOrder, setSortOrder] = useState(
    initial ? String(initial.sortOrder) : '',
  );
  const [imageUrl, setImageUrl] = useState(initial?.imageUrl ?? '');

  const canSubmit = !busy && name.trim().length > 0;

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!canSubmit) return;
    const parsedSort = sortOrder.trim() === '' ? undefined : Number(sortOrder);
    void onSubmit({
      name: name.trim(),
      slug: slug.trim(),
      sortOrder:
        parsedSort !== undefined && Number.isFinite(parsedSort)
          ? parsedSort
          : undefined,
      imageUrl: imageUrl.trim(),
    });
  }

  return (
    <form className="cat-form" onSubmit={submit}>
      <div className="cat-form-grid">
        <label className="login-field" htmlFor="cat-name">
          Name
          <input
            id="cat-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            autoFocus
          />
        </label>

        {mode === 'create' && (
          <label className="login-field" htmlFor="cat-slug">
            Slug (optional)
            <input
              id="cat-slug"
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              placeholder="auto from name"
            />
          </label>
        )}

        <label className="login-field" htmlFor="cat-sort">
          Sort order (optional)
          <input
            id="cat-sort"
            type="number"
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value)}
            placeholder="0"
          />
        </label>

        <label className="login-field cat-form-wide" htmlFor="cat-image">
          Image URL (optional)
          <input
            id="cat-image"
            value={imageUrl}
            onChange={(e) => setImageUrl(e.target.value)}
            placeholder="https://…"
          />
        </label>
      </div>

      <div className="cat-form-actions">
        <button type="submit" className="btn" disabled={!canSubmit}>
          {busy ? 'Saving…' : mode === 'create' ? 'Add category' : 'Save'}
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={busy}
          onClick={onCancel}
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
