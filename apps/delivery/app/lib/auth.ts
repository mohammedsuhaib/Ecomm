import type { UserDto } from './types';

const STORAGE_KEY = 'tb.delivery.auth.v1';

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

function hasStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

export function getStoredAuth(): StoredAuth | null {
  if (!hasStorage()) return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredAuth>;
    if (
      typeof parsed?.accessToken === 'string' &&
      typeof parsed?.refreshToken === 'string' &&
      parsed?.user
    ) {
      return parsed as StoredAuth;
    }
    return null;
  } catch {
    return null;
  }
}

export const getAccessToken = (): string | null => getStoredAuth()?.accessToken ?? null;
export const getRefreshToken = (): string | null => getStoredAuth()?.refreshToken ?? null;
export const getStoredUser = (): UserDto | null => getStoredAuth()?.user ?? null;

export function saveAuth(auth: StoredAuth): void {
  if (!hasStorage()) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  } catch { /* storage full */ }
}

export function updateTokens(tokens: { accessToken: string; refreshToken: string }): void {
  const current = getStoredAuth();
  if (!current) return;
  saveAuth({ ...current, ...tokens });
}

export function clearAuth(): void {
  if (!hasStorage()) return;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch { /* ignore */ }
}
