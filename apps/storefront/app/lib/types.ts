// Typed models mirroring the public REST API contract (NEXT_PUBLIC_API_BASE_URL).
// These match the Spring `catalog` + `serviceability` module responses for M2.
// NOTE: cost price is intentionally never exposed by the API and so is absent here.

export interface ProductVariant {
  id: string;
  label: string;
  sellingPrice: number; // decimal rupees
  mrp: number | null; // decimal rupees; null when no MRP/strikethrough
  available: boolean;
}

export interface Product {
  id: string;
  name: string;
  slug: string;
  categoryId: string;
  description: string;
  vegMarker: boolean; // true => veg (green dot), false => non-veg (red dot)
  imageUrl: string | null;
  available: boolean;
  variants: ProductVariant[];
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  imageUrl: string | null;
  sortOrder: number;
}

export interface Store {
  name: string;
  address: string;
  openingTime: string;
  closingTime: string;
  deliveryRadiusMeters: number;
  minOrderValue: number;
  lat: number;
  lng: number;
}

export interface ServiceabilityResult {
  serviceable: boolean;
  distanceMeters: number;
  radiusMeters: number;
  storeName: string;
}

// Spring Data style page envelope used by list endpoints.
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}
