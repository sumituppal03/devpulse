package com.devpulse.shared.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * GitHub signs every webhook payload with HMAC-SHA256 using a shared secret
 * configured both in the GitHub repo's webhook settings AND here. This proves
 * the request genuinely came from GitHub — not a random caller pretending to.
 */
@Component
public class GitHubWebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final String webhookSecret;

    public GitHubWebhookSignatureVerifier(@Value("${github.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isValid(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String providedSignature = signatureHeader.substring("sha256=".length());
        String computedSignature = computeHmac(rawPayload);

        // Constant-time comparison — prevents an attacker from learning
        // anything about the correct signature from response timing.
        return MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                computedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
}
