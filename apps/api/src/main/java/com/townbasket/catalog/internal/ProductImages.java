package com.townbasket.catalog.internal;

import com.townbasket.shared.BusinessRuleException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Turning whatever a staff member picked on their phone into something safe to
 * store and fast to serve. Pure functions, no Spring, no network — the rules
 * that matter most here are the ones worth testing without a container.
 *
 * <p><strong>Why re-encode rather than store the upload.</strong> A photo
 * straight off a phone camera is several megabytes at 4000px, and this
 * storefront exists to be quick on cheap phones over mobile data. Decoding and
 * re-writing the pixels also drops every EXIF block on the floor — including
 * the GPS coordinates of wherever the photo was taken, which nobody intends to
 * publish when they upload a picture of a packet of flour.
 *
 * <p><strong>Why the format is decided by the bytes.</strong> The declared
 * content type and the filename both come from the client and neither is
 * evidence. SVG is refused outright and is the reason this checks at all: it is
 * a document that can carry script, so an SVG served back from our own domain
 * is stored XSS. Only JPEG and PNG are admitted, and both leave here as JPEG.
 */
final class ProductImages {

    /** Refuse before decoding: a decoder is a big attack surface to hand a hostile file. */
    static final int MAX_UPLOAD_BYTES = 6 * 1024 * 1024;

    /**
     * Longest edge after scaling. A product thumbnail is shown a few hundred
     * pixels wide; 1000 leaves room for a retina grid without paying for a
     * photo nobody will ever see at full size.
     */
    static final int MAX_EDGE_PX = 1000;

    private static final float JPEG_QUALITY = 0.85f;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'};

    private ProductImages() {
    }

    /**
     * Validate, decode, scale and re-encode an upload as JPEG.
     *
     * @throws BusinessRuleException with copy a staff member can act on — this
     *     surfaces in the admin product form
     */
    static byte[] normalise(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new BusinessRuleException("That file is empty. Please choose an image.");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new BusinessRuleException(
                    "That image is larger than " + (MAX_UPLOAD_BYTES / (1024 * 1024))
                            + " MB. Please choose a smaller one.");
        }
        if (!startsWith(upload, JPEG_MAGIC) && !startsWith(upload, PNG_MAGIC)) {
            throw new BusinessRuleException(
                    "Only JPEG and PNG images can be uploaded. If this is an SVG, "
                            + "HEIC or WebP file, export it as a JPEG first.");
        }
        BufferedImage source = read(upload);
        if (source == null) {
            throw new BusinessRuleException(
                    "That file could not be read as an image. It may be damaged — "
                            + "try opening and re-saving it.");
        }
        return encodeJpeg(flattenAndScale(source));
    }

    /** {@code null} rather than an exception when the bytes are not a decodable image. */
    private static BufferedImage read(byte[] upload) {
        try {
            return ImageIO.read(new ByteArrayInputStream(upload));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Scale to {@link #MAX_EDGE_PX} and drop the alpha channel.
     *
     * <p>The flatten is not cosmetic: JPEG has no alpha, so a transparent PNG
     * written straight out comes back with the see-through parts rendered
     * black. Compositing onto white first is what a person expects to see when
     * they upload a packshot cut out on a transparent background.
     */
    private static BufferedImage flattenAndScale(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double factor = longest > MAX_EDGE_PX ? (double) MAX_EDGE_PX / longest : 1.0;
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the image", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * The object key for a freshly uploaded image.
     *
     * <p>Generated here, never derived from the client's filename: a name
     * arriving over the wire can carry path separators, traversal sequences or
     * another product's key, and none of that is worth defending against when
     * a random name costs nothing. It also means re-uploading never silently
     * overwrites the picture another product is still using.
     */
    static String newKey() {
        return "products/" + UUID.randomUUID() + ".jpg";
    }

    /**
     * The object key inside {@code publicBaseUrl}, or empty when the URL is not
     * ours to manage.
     *
     * <p>This is the guard that decides whether deleting a product also deletes
     * a stored object. Product images predate this feature: the seeded
     * catalogue points at relative paths like {@code /images/products/x.jpg}
     * that the storefront serves from its own bundle, and staff may paste a
     * link to someone else's CDN. Neither is ours to delete, and an image URL
     * is not permission to issue a delete against an arbitrary bucket path.
     */
    static Optional<String> keyOf(String imageUrl, String publicBaseUrl) {
        if (imageUrl == null || publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return Optional.empty();
        }
        String prefix = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        if (!imageUrl.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = imageUrl.substring(prefix.length());
        // A key that escapes the prefix is not something we wrote.
        if (key.isBlank() || key.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
