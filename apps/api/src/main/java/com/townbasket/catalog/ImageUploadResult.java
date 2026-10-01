package com.townbasket.catalog;

/**
 * Where an uploaded product or category image now lives.
 *
 * <p>The upload is deliberately not tied to a product id: the admin form needs
 * a picture before the product it belongs to exists, and a two-step "create the
 * product, then go back and add the photo" is the kind of flow staff abandon
 * halfway. The caller saves this URL as the product's or category's
 * {@code imageUrl} like any other value.
 *
 * @param url public, CDN-cacheable URL of the stored image
 */
public record ImageUploadResult(String url) {
}
