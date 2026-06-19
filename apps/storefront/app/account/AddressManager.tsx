'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  ApiError,
  addAddress,
  deleteAddress,
  listAddresses,
  updateAddress,
} from '@/app/lib/api';
import { loadServiceability } from '@/app/lib/serviceability';
import LocationPicker from '@/app/components/LocationPicker';
import type { AddressInput, SavedAddress } from '@/app/lib/types';

interface FormState {
  label: string;
  line: string;
  lat: string;
  lng: string;
  isDefault: boolean;
}

const EMPTY_FORM: FormState = {
  label: '',
  line: '',
  lat: '',
  lng: '',
  isDefault: false,
};

function toInput(f: FormState): AddressInput | null {
  const lat = Number.parseFloat(f.lat);
  const lng = Number.parseFloat(f.lng);
  const coordsValid =
    !Number.isNaN(lat) &&
    !Number.isNaN(lng) &&
    lat >= -90 &&
    lat <= 90 &&
    lng >= -180 &&
    lng <= 180;
  if (f.line.trim().length < 4 || !coordsValid) return null;
  return {
    label: f.label.trim() || null,
    line: f.line.trim(),
    lat,
    lng,
    isDefault: f.isDefault,
  };
}

/** Saved-addresses panel: list + add + edit + delete + set-default. */
export default function AddressManager() {
  const [addresses, setAddresses] = useState<SavedAddress[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // `editingId === null` means the form is in "add" mode; a number means edit.
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAddresses(await listAddresses());
    } catch {
      setError('Could not load your saved addresses.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function openAdd() {
    const stored = loadServiceability();
    setEditingId(null);
    setForm({
      ...EMPTY_FORM,
      // Prefill coords from the delivery location, like checkout does.
      lat: stored ? String(stored.lat) : '',
      lng: stored ? String(stored.lng) : '',
      isDefault: addresses.length === 0,
    });
    setShowForm(true);
  }

  function openEdit(a: SavedAddress) {
    setEditingId(a.id);
    setForm({
      label: a.label ?? '',
      line: a.line,
      lat: String(a.lat),
      lng: String(a.lng),
      isDefault: a.isDefault,
    });
    setShowForm(true);
  }

  function closeForm() {
    setShowForm(false);
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const input = toInput(form);
    if (!input || saving) return;
    setSaving(true);
    setError(null);
    try {
      if (editingId == null) await addAddress(input);
      else await updateAddress(editingId, input);
      closeForm();
      await load();
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? 'That address no longer exists.'
          : 'Could not save the address. Please try again.',
      );
      setSaving(false);
    }
  }

  async function remove(id: number) {
    setBusyId(id);
    setError(null);
    try {
      await deleteAddress(id);
      await load();
    } catch {
      setError('Could not delete that address. Please try again.');
    } finally {
      setBusyId(null);
    }
  }

  // Set-default reuses PUT with isDefault=true (backend single-default rule).
  async function setDefault(a: SavedAddress) {
    setBusyId(a.id);
    setError(null);
    try {
      await updateAddress(a.id, {
        label: a.label,
        line: a.line,
        lat: a.lat,
        lng: a.lng,
        isDefault: true,
      });
      await load();
    } catch {
      setError('Could not update the default address.');
    } finally {
      setBusyId(null);
    }
  }

  const formInput = toInput(form);

  return (
    <section className="account-section">
      <div className="account-section-head">
        <h2 className="section-title" style={{ margin: 0 }}>
          Saved addresses
        </h2>
        {!showForm && (
          <button type="button" className="btn btn-outline" onClick={openAdd}>
            Add address
          </button>
        )}
      </div>

      {error && <p className="notice error">{error}</p>}

      {showForm && (
        <form className="checkout-section" onSubmit={submit}>
          <div className="field">
            <label htmlFor="addr-label">Label (optional)</label>
            <input
              id="addr-label"
              placeholder="Home, Work…"
              value={form.label}
              onChange={(e) => setForm({ ...form, label: e.target.value })}
            />
          </div>
          <div className="field">
            <label htmlFor="addr-line">Address</label>
            <textarea
              id="addr-line"
              rows={3}
              placeholder="Flat / house no, building, street, area, landmark"
              value={form.line}
              onChange={(e) => setForm({ ...form, line: e.target.value })}
              required
            />
          </div>
          <div className="field">
            <label>Location</label>
            <LocationPicker
              lat={form.lat}
              lng={form.lng}
              onChange={(la, ln) =>
                setForm((prev) => ({
                  ...prev,
                  lat: String(la),
                  lng: String(ln),
                }))
              }
            />
          </div>
          <label className="radio-row" style={{ alignItems: 'center' }}>
            <input
              type="checkbox"
              checked={form.isDefault}
              onChange={(e) =>
                setForm({ ...form, isDefault: e.target.checked })
              }
            />
            <span>Set as default address</span>
          </label>
          <div className="account-form-actions">
            <button
              type="submit"
              className="btn"
              disabled={!formInput || saving}
            >
              {saving
                ? 'Saving…'
                : editingId == null
                  ? 'Add address'
                  : 'Save changes'}
            </button>
            <button
              type="button"
              className="btn btn-outline"
              onClick={closeForm}
              disabled={saving}
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <p className="muted">Loading addresses…</p>
      ) : addresses.length === 0 && !showForm ? (
        <p className="muted">No saved addresses yet.</p>
      ) : (
        <ul className="address-list">
          {addresses.map((a) => (
            <li key={a.id} className="address-row">
              <div className="address-info">
                <span className="address-label">
                  {a.label || 'Address'}
                  {a.isDefault && (
                    <span className="default-tag">Default</span>
                  )}
                </span>
                <span className="muted">{a.line}</span>
                <span className="muted address-coords">
                  {a.lat.toFixed(5)}, {a.lng.toFixed(5)}
                </span>
              </div>
              <div className="address-actions">
                {!a.isDefault && (
                  <button
                    type="button"
                    className="link-action"
                    disabled={busyId === a.id}
                    onClick={() => setDefault(a)}
                  >
                    Set default
                  </button>
                )}
                <button
                  type="button"
                  className="link-action"
                  disabled={busyId === a.id}
                  onClick={() => openEdit(a)}
                >
                  Edit
                </button>
                <button
                  type="button"
                  className="link-danger"
                  disabled={busyId === a.id}
                  onClick={() => remove(a.id)}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
