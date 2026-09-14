package com.townbasket.catalog.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage for product images — DigitalOcean Spaces, which speaks S3
 * (ARCHITECTURE §7b).
 *
 * <p>Unset is a supported state, not a broken one: the app runs with image
 * upload switched off, the admin form falls back to pasting a URL, and the
 * seeded catalogue keeps serving the images committed in the storefront bundle.
 * That is the default for local development, where nobody should need cloud
 * credentials to run the stack.
 *
 * <p>Use a bucket separate from the nightly backup bucket. These objects are
 * world-readable by design — they are product photos on a public shop — and
 * database dumps must never live behind the same access policy.
 *
 * @param bucket        Spaces bucket name, e.g. {@code town-basket-media}
 * @param endpoint      regional S3 endpoint, e.g.
 *                      {@code https://blr1.digitaloceanspaces.com}
 * @param region        Spaces ignores the region but the S3 client insists on
 *                      one to sign with; the bucket's own region is the honest
 *                      value
 * @param publicBaseUrl where those objects are readable from — the bucket's
 *                      public origin, or its CDN alias. Stored image URLs are
 *                      built from this, and it is what decides later whether an
 *                      image belongs to us (see
 *                      {@link ProductImages#keyOf(String, String)}), so
 *                      changing it orphans the images already written under the
 *                      old one.
 * @param accessKey     Spaces access key id
 * @param secretKey     Spaces secret — a secret, from the environment, never git
 */
@ConfigurationProperties(prefix = "townbasket.catalog.images")
record ProductImageProperties(
        String bucket,
        String endpoint,
        String region,
        String publicBaseUrl,
        String accessKey,
        String secretKey) {

    ProductImageProperties {
        bucket = trim(bucket);
        endpoint = trim(endpoint);
        region = trim(region).isEmpty() ? "us-east-1" : trim(region);
        publicBaseUrl = stripTrailingSlash(trim(publicBaseUrl));
        accessKey = trim(accessKey);
        secretKey = trim(secretKey);
    }

    /**
     * Uploads are only offered when every part is present. A half-configured
     * bucket would fail at the moment a staff member picks a file, which is the
     * worst time to discover it.
     */
    boolean configured() {
        return !bucket.isEmpty()
                && !endpoint.isEmpty()
                && !accessKey.isEmpty()
                && !secretKey.isEmpty()
                && !publicBaseUrl.isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
