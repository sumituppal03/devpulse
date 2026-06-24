package com.devpulse.shared.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubWebhookSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";
    private final GitHubWebhookSignatureVerifier verifier = new GitHubWebhookSignatureVerifier(SECRET);

    @Test
    void isValid_withCorrectSignature_returnsTrue() {
        String payload = "{\"action\":\"opened\"}";
        String correctSignature = "sha256=" + computeExpectedHmac(payload, SECRET);

        assertThat(verifier.isValid(payload, correctSignature)).isTrue();
    }

    @Test
    void isValid_withTamperedPayload_returnsFalse() {
        String originalPayload = "{\"action\":\"opened\"}";
        String tamperedPayload = "{\"action\":\"closed\"}";
        String signatureForOriginal = "sha256=" + computeExpectedHmac(originalPayload, SECRET);

        // The signature was computed for a different payload than the one
        // being verified — this is exactly what would happen if someone
        // intercepted and altered a request in transit.
        assertThat(verifier.isValid(tamperedPayload, signatureForOriginal)).isFalse();
    }

    @Test
    void isValid_withWrongSecret_returnsFalse() {
        String payload = "{\"action\":\"opened\"}";
        String signatureWithWrongSecret = "sha256=" + computeExpectedHmac(payload, "a-different-secret");

        assertThat(verifier.isValid(payload, signatureWithWrongSecret)).isFalse();
    }

    @Test
    void isValid_withMissingShaPrefix_returnsFalse() {
        String payload = "{\"action\":\"opened\"}";
        String malformedHeader = computeExpectedHmac(payload, SECRET); // no "sha256=" prefix

        assertThat(verifier.isValid(payload, malformedHeader)).isFalse();
    }

    @Test
    void isValid_withNullSignatureHeader_returnsFalse() {
        assertThat(verifier.isValid("{}", null)).isFalse();
    }

    /** Independently computes HMAC-SHA256, mirroring exactly what GitHub itself does. */
    private String computeExpectedHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}