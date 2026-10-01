'use client';

import { useRef, useState, type FormEvent } from 'react';
import {
  ApiError,
  AuthRequiredError,
  createCategory,
  deleteCategory,
  serverMessage,
  updateCategory,
  uploadCatalogImage,
} from '@/app/lib/api';
import type { Category } from '@/app/lib/types';
import { ListSkeleton } from './Skeleton';

/**
 * Categories panel: lists categories (name / sort order / image), supports
 * add, inline edit (rename, re-sort, re-image) and delete. Deleting a category
 * that still has products returns 422 from the backend — we surface that as a
 * clear "move or remove its products first" message rather than a generic error.
 * Mirrors ChangePassword's busy / error / success pattern.
 *
 * <p>A failed DELETE reports AT THE ROW, and ONLY there. The banner sits above
 * the list, so staff who scrolled down to a category, hit Delete and got a 422
 * saw the row stay put with the explanation off screen — indistinguishable
 * from nothing having happened. Reporting in both places was the first attempt
 * at that and was worse: the identical sentence appeared twice on one screen,
 * which reads as two separate failures. The banner is now for the add/edit
 * forms, which have no row to point at.
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
  /** The category a delete just failed on, and why — rendered under that row. */
  const [rowError, setRowError] = useState<{ id: number; message: string } | null>(null);

  /**
   * Wipe both messages. Called at the start of every action, so a refusal that
   * has been dealt with stops being shown: the row message used to survive
   * until the next DELETE, which meant "move its products first" sat under a
   * category long after the products had been moved — and looked like the
   * delete had just failed again.
   */
  function clearMessages() {
    setError(null);
    setRowError(null);
  }

  function mapError(err: unknown, action: 'delete' | 'save'): string {
    if (err instanceof AuthRequiredError) return 'Session expired — please log in again.';
    if (err instanceof ApiError) {
      if (action === 'delete' && err.status === 422) {
        // The server names the rule it enforced; prefer its wording over a
        // second copy of the same sentence that can drift from it.
        return (
          serverMessage(err) ??
          'This category still has products. Move or remove them before deleting it.'
        );
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
    clearMessages();
    try {
      await deleteCategory(cat.id);
      await onChanged();
    } catch (err) {
      // At the row only — never also in the banner, or staff read one refusal
      // as two.
      setRowError({ id: cat.id, message: mapError(err, 'delete') });
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
            clearMessages();
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
            clearMessages();
            try {
              await createCategory({
                name: payload.name,
                slug: payload.slug || undefined,
                sortOrder: payload.sortOrder,
                imageUrl: payload.imageUrl || null,
                nameKn: payload.nameKn || null,
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
        <ListSkeleton label="Loading categories…" rows={6} />
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
                    clearMessages();
                    try {
                      await updateCategory(cat.id, {
                        name: payload.name,
                        sortOrder: payload.sortOrder,
                        imageUrl: payload.imageUrl || null,
                        nameKn: payload.nameKn || null,
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
                  <span className="cat-name">
                    {cat.name}
                    {cat.nameKn ? <span className="muted"> · {cat.nameKn}</span> : null}
                  </span>
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
                      clearMessages();
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
                {rowError?.id === cat.id && (
                  <p className="cat-row-error" role="alert">
                    {rowError.message}
                  </p>
                )}
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
  nameKn: string;
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
  const [nameKn, setNameKn] = useState(initial?.nameKn ?? '');
  const [slug, setSlug] = useState(initial?.slug ?? '');
  const [sortOrder, setSortOrder] = useState(
    initial ? String(initial.sortOrder) : '',
  );
  const [imageUrl, setImageUrl] = useState(initial?.imageUrl ?? '');
  const [imageBusy, setImageBusy] = useState(false);
  const [imageError, setImageError] = useState<string | null>(null);
  const imageInputRef = useRef<HTMLInputElement | null>(null);

  // Saving mid-upload would store the old URL and strand the new object.
  const canSubmit = !busy && !imageBusy && name.trim().length > 0;

  async function onPickImage(file: File | undefined) {
    if (!file) return;
    setImageBusy(true);
    setImageError(null);
    try {
      const { url } = await uploadCatalogImage(file);
      setImageUrl(url);
    } catch (err) {
      if (err instanceof AuthRequiredError) {
        setImageError('Session expired — please log in again.');
      } else {
        // The server's message names the actual problem (not a JPEG/PNG, too
        // large, uploads not configured), and each needs a different fix.
        setImageError(
          serverMessage(err) ?? 'Could not upload that image. Please try again.',
        );
      }
    } finally {
      setImageBusy(false);
      // Lets the same file be picked again after a failure.
      if (imageInputRef.current) imageInputRef.current.value = '';
    }
  }

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!canSubmit) return;
    const parsedSort = sortOrder.trim() === '' ? undefined : Number(sortOrder);
    void onSubmit({
      name: name.trim(),
      nameKn: nameKn.trim(),
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

        <label className="login-field" htmlFor="cat-namekn">
          Kannada name (optional)
          <input
            id="cat-namekn"
            lang="kn"
            value={nameKn}
            onChange={(e) => setNameKn(e.target.value)}
            placeholder="ಉದಾ: ಅಕ್ಕಿ ಮತ್ತು ಬೇಳೆಗಳು"
          />
          <span className="field-hint neutral">
            Write a translation here. Left blank, it is filled in by sound
            (transliteration).
          </span>
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
          Category image (optional)
          <input
            id="cat-image"
            value={imageUrl}
            onChange={(e) => setImageUrl(e.target.value)}
            placeholder="Upload below, or paste an image URL"
          />
          <div className="image-upload-row">
            <input
              ref={imageInputRef}
              id="cat-image-file"
              type="file"
              accept="image/jpeg,image/png"
              aria-label="Upload category image"
              disabled={busy || imageBusy}
              onChange={(e) => void onPickImage(e.target.files?.[0])}
            />
            {imageBusy && <span className="field-hint neutral">Uploading…</span>}
          </div>
          {imageUrl ? (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img
              key={imageUrl}
              src={imageUrl}
              alt=""
              className="image-upload-preview"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
            />
          ) : null}
          {imageError ? (
            <span className="field-hint error">{imageError}</span>
          ) : (
            <span className="field-hint neutral">
              JPEG or PNG, resized automatically. The old picture is removed
              when you replace it or delete the category.
            </span>
          )}
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
