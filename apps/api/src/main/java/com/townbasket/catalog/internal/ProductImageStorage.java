package com.townbasket.catalog.internal;

import com.townbasket.shared.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores product images in DigitalOcean Spaces and removes them again when the
 * product that referenced them is deleted or given a different picture.
 *
 * <p>Follows the Web Push channel's shape: unconfigured is a normal state the
 * app boots and runs in, announced once at startup, and the one endpoint that
 * needs it refuses with copy staff can act on rather than a 500.
 */
@Component
@EnableConfigurationProperties(ProductImageProperties.class)
public class ProductImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ProductImageStorage.class);

    private final ProductImageProperties properties;
    private S3Client client;

    ProductImageStorage(ProductImageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!properties.configured()) {
            log.info("Product image upload disabled — no object storage configured "
                    + "(set townbasket.catalog.images.* to enable). Staff can still paste an image URL.");
            return;
        }
        try {
            client = S3Client.builder()
                    .endpointOverride(URI.create(properties.endpoint()))
                    .region(Region.of(properties.region()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                    .httpClient(UrlConnectionHttpClient.builder().build())
                    .build();
            log.info("Product image upload enabled — bucket {} at {}",
                    properties.bucket(), properties.endpoint());
        } catch (RuntimeException e) {
            // Never stop the application booting over an optional add-on; the
            // catalogue itself works fine without uploads.
            log.error("Product image upload could not start; check townbasket.catalog.images.*: {}",
                    e.toString());
        }
    }

    public boolean isEnabled() {
        return client != null;
    }

    /**
     * Validate and store an upload, returning the public URL to save on the
     * product.
     *
     * @throws BusinessRuleException if uploads are not configured, or the file
     *     is not an image we accept (mapped to 422)
     */
    public String upload(byte[] upload) {
        if (!isEnabled()) {
            throw new BusinessRuleException(
                    "Image upload isn't set up on this deployment yet. "
                            + "Paste an image URL instead, or ask an administrator to configure storage.");
        }
        byte[] jpeg = ProductImages.normalise(upload);
        String key = ProductImages.newKey();
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType("image/jpeg")
                        // Product photos are public by definition — the storefront
                        // loads them straight from the bucket with no credentials.
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        // Immutable: every upload gets a fresh key, so the object at
                        // one key never changes and caches can hold it indefinitely.
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(jpeg));
        return properties.publicBaseUrl() + "/" + key;
    }

    /**
     * Delete the object an image URL points at, if it is one of ours.
     *
     * <p>Best-effort on purpose. This runs while deleting or re-imaging a
     * product, and a bucket that is briefly unreachable must not fail — or
     * worse, roll back — a catalogue edit the staff member has already been
     * told about. A leftover object costs a fraction of a paisa; a delete that
     * appears to fail costs someone their afternoon.
     */
    void deleteByUrl(String imageUrl) {
        if (!isEnabled()) {
            return;
        }
        ProductImages.keyOf(imageUrl, properties.publicBaseUrl()).ifPresent(key -> {
            try {
                client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build());
            } catch (RuntimeException e) {
                log.warn("Could not delete product image {}: {}", key, e.toString());
            }
        });
    }
}
