'use client';

import { useState } from 'react';
import { useAuth } from '@/app/components/AuthProvider';
import { ApiError, updateProfile } from '@/app/lib/api';
import type { UserDto } from '@/app/lib/types';

const MAX_NAME = 80;

/**
 * Inline-editable display name (Change 2 consumer). Prefilled from `user.name`;
 * Save → PUT /me → refresh the cached user via AuthProvider so the header and
 * account card reflect the new name. Validates 1..80 chars client-side and
 * surfaces field + request (ApiError) errors.
 */
export default function ProfileEditor({ user }: { user: UserDto }) {
  const { refreshUser } = useAuth();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(user.name ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const trimmed = name.trim();
  const valid = trimmed.length >= 1 && trimmed.length <= MAX_NAME;

  function openEdit() {
    setName(user.name ?? '');
    setError(null);
    setEditing(true);
  }

  function cancel() {
    setEditing(false);
    setError(null);
    setName(user.name ?? '');
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || saving) return;
    setSaving(true);
    setError(null);
    try {
      await updateProfile(trimmed);
      // Sync the cached user (header + this card) from the server.
      await refreshUser();
      setEditing(false);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(
          err.status === 400
            ? 'Please enter a name between 1 and 80 characters.'
            : 'Could not update your name. Please try again.',
        );
      } else {
        setError('Could not update your name. Please try again.');
      }
      setSaving(false);
    }
  }

  if (!editing) {
    return (
      <div className="profile-card">
        <div className="account-section-head">
          <p style={{ margin: 0 }}>
            <strong>{user.name?.trim() || 'Customer'}</strong>
          </p>
          <button type="button" className="link-action" onClick={openEdit}>
            Edit name
          </button>
        </div>
        {user.phone && (
          <p className="muted" style={{ margin: 0 }}>
            +91 {user.phone}
          </p>
        )}
        {user.email && (
          <p className="muted" style={{ margin: 0 }}>
            {user.email}
          </p>
        )}
      </div>
    );
  }

  return (
    <form className="profile-card" onSubmit={submit}>
      <div className="field">
        <label htmlFor="profile-name">Display name</label>
        <input
          id="profile-name"
          value={name}
          maxLength={MAX_NAME}
          autoFocus
          onChange={(e) => setName(e.target.value)}
          aria-invalid={!valid}
        />
        {!valid && trimmed.length === 0 && (
          <span className="muted" style={{ fontSize: '0.8rem' }}>
            Name is required (1–80 characters).
          </span>
        )}
      </div>

      {error && <p className="notice error">{error}</p>}

      <div className="account-form-actions">
        <button type="submit" className="btn" disabled={!valid || saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        <button
          type="button"
          className="btn btn-outline"
          onClick={cancel}
          disabled={saving}
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
