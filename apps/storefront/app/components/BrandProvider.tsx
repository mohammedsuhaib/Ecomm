'use client';

import { createContext, useContext } from 'react';
import type { PublicBrand } from '@/app/lib/brand';

/**
 * The brand's public values for client components. The full brand is
 * server-only (app/lib/brand.ts); the root layout hands down just the name and
 * slug, which is all a client component has needed so far.
 */
const BrandContext = createContext<PublicBrand>({ name: '', slug: 'store' });

export function BrandProvider({
  brand,
  children,
}: {
  brand: PublicBrand;
  children: React.ReactNode;
}) {
  return <BrandContext.Provider value={brand}>{children}</BrandContext.Provider>;
}

export function useBrand(): PublicBrand {
  return useContext(BrandContext);
}
