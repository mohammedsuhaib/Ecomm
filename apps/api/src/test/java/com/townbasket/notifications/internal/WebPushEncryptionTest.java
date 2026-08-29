package com.townbasket.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves the Web Push crypto path works end-to-end short of the network: a
 * generated VAPID key pair signs the request and the payload is encrypted for a
 * subscription's key material (RFC 8291 aes128gcm).
 *
 * <p>This is the part most likely to break silently in production — a missing
 * BouncyCastle provider or an incompatible key encoding shows up as pushes that
 * are simply never delivered. {@code preparePost} performs the full signing and
 * encryption without contacting a push service, so it can be asserted offline.
 */
class WebPushEncryptionTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void signsAndEncryptsAPushRequest() throws Exception {
        KeyPair vapid = generateP256();
        String vapidPublic = base64url(Utils.encode((ECPublicKey) vapid.getPublic()));
        String vapidPrivate = base64url(Utils.encode((ECPrivateKey) vapid.getPrivate()));

        // Stand in for the key material a browser mints when it subscribes.
        KeyPair browser = generateP256();
        String p256dh = base64url(Utils.encode((ECPublicKey) browser.getPublic()));
        byte[] authSecret = new byte[16];
        RANDOM.nextBytes(authSecret);
        String auth = base64url(authSecret);

        String plaintext = "{\"title\":\"Order delivered\",\"body\":\"Thank you!\"}";
        PushService pushService = new PushService(vapidPublic, vapidPrivate, "mailto:support@town-basket.com");
        Notification notification = new Notification(
                "https://fcm.googleapis.com/fcm/send/test-endpoint",
                p256dh, auth,
                plaintext.getBytes(StandardCharsets.UTF_8));

        HttpPost request = pushService.preparePost(notification, Encoding.AES128GCM);

        assertThat(request.getURI().toString()).endsWith("/test-endpoint");
        assertThat(header(request, "Content-Encoding")).isEqualTo("aes128gcm");
        // VAPID: the request is signed with our server key so the push service
        // can attribute it to this application.
        assertThat(header(request, "Authorization")).startsWith("vapid");
        assertThat(header(request, "Authorization")).contains(vapidPublic);

        byte[] body = request.getEntity().getContent().readAllBytes();
        assertThat(body).isNotEmpty();
        // The payload must go out encrypted, never as the JSON we handed in.
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain("Order delivered");
        assertThat(body.length).isGreaterThan(plaintext.length());
    }

    private static KeyPair generateP256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String header(HttpPost request, String name) {
        return request.getFirstHeader(name) == null ? null : request.getFirstHeader(name).getValue();
    }
}
