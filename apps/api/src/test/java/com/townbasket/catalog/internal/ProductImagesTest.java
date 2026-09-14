package com.townbasket.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.shared.BusinessRuleException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The rules that decide what a staff member is allowed to put on the shop.
 *
 * <p>Pure unit test — no Spring, no Docker, no bucket. That is the point: these
 * are the checks standing between an arbitrary uploaded file and a URL served
 * from our own domain, so they should run on every build rather than only when
 * Docker happens to be available.
 */
class ProductImagesTest {

    @Test
    void anOversizedPhotoIsScaledDownAndReEncodedAsJpeg() {
        byte[] huge = png(2400, 1600);

        byte[] out = ProductImages.normalise(huge);

        BufferedImage decoded = read(out);
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                .isEqualTo(ProductImages.MAX_EDGE_PX);
        // Aspect ratio survives: 2400x1600 is 3:2, so 1000 wide means 667 tall.
        assertThat(decoded.getHeight()).isEqualTo(667);
        // A phone-sized upload must come out dramatically smaller — the whole
        // reason for re-encoding is the customer on a cheap phone and mobile data.
        assertThat(out.length).isLessThan(huge.length);
        assertThat(out[0]).isEqualTo((byte) 0xFF);
        assertThat(out[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void anImageSmallerThanTheLimitKeepsItsDimensions() {
        BufferedImage decoded = read(ProductImages.normalise(png(300, 200)));

        assertThat(decoded.getWidth()).isEqualTo(300);
        assertThat(decoded.getHeight()).isEqualTo(200);
    }

    @Test
    void anSvgIsRefusedEvenThoughItIsAnImage() {
        // The reason this check exists: an SVG is a document that can carry
        // script, so one served back from our own origin is stored XSS. It is
        // refused on its bytes, not on the filename or content type the client
        // claimed — neither of those is evidence.
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ProductImages.normalise(svg))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("JPEG and PNG");
    }

    @Test
    void aFileRenamedToLookLikeAnImageIsRefused() {
        byte[] notAnImage = "PK-- this is really a zip".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ProductImages.normalise(notAnImage))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anEmptyOrOversizeUploadIsRefusedBeforeDecoding() {
        assertThatThrownBy(() -> ProductImages.normalise(new byte[0]))
                .isInstanceOf(BusinessRuleException.class);

        // Big enough to trip the cap, and shaped like a JPEG so the size check is
        // demonstrably what rejects it rather than the magic-byte check.
        byte[] oversize = new byte[ProductImages.MAX_UPLOAD_BYTES + 1];
        oversize[0] = (byte) 0xFF;
        oversize[1] = (byte) 0xD8;
        oversize[2] = (byte) 0xFF;
        assertThatThrownBy(() -> ProductImages.normalise(oversize))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("MB");
    }

    @Test
    void transparencyBecomesWhiteRatherThanBlack() {
        // JPEG has no alpha. Written naively, a packshot cut out on a
        // transparent background comes back with black where the cut-out was.
        BufferedImage transparent = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage flattened = read(ProductImages.normalise(toPng(transparent)));

        Color corner = new Color(flattened.getRGB(0, 0));
        assertThat(corner.getRed()).isGreaterThan(240);
        assertThat(corner.getGreen()).isGreaterThan(240);
        assertThat(corner.getBlue()).isGreaterThan(240);
    }

    @Test
    void eachUploadGetsItsOwnKeyUnderTheProductsPrefix() {
        String first = ProductImages.newKey();
        String second = ProductImages.newKey();

        assertThat(first).startsWith("products/").endsWith(".jpg");
        // Keys are generated, never taken from the client's filename — so one
        // upload can never overwrite the picture another product is using.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void onlyOurOwnImagesAreEligibleForDeletion() {
        String base = "https://town-basket-media.blr1.cdn.digitaloceanspaces.com";

        assertThat(ProductImages.keyOf(base + "/products/abc.jpg", base))
                .contains("products/abc.jpg");

        // The seeded catalogue points at relative paths the storefront serves
        // from its own bundle; staff may paste someone else's CDN link. Deleting
        // a product must not try to issue a delete for either, and an image URL
        // is not permission to delete an arbitrary path in our bucket.
        assertThat(ProductImages.keyOf("/images/products/amul-butter.jpg", base)).isEmpty();
        assertThat(ProductImages.keyOf("https://example.com/products/abc.jpg", base)).isEmpty();
        assertThat(ProductImages.keyOf(base + "/../secrets/dump.sql", base)).isEmpty();
        assertThat(ProductImages.keyOf(null, base)).isEmpty();
        assertThat(ProductImages.keyOf(base + "/products/abc.jpg", "")).isEmpty();
    }

    // ---- helpers -----------------------------------------------------------

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            // Noise, not a flat fill: a single colour compresses to almost
            // nothing and would make the size assertion meaningless.
            for (int x = 0; x < width; x += 7) {
                g.setColor(new Color((x * 37) % 255, (x * 91) % 255, (x * 17) % 255));
                g.fillRect(x, 0, 7, height);
            }
        } finally {
            g.dispose();
        }
        return toPng(image);
    }

    private static byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
