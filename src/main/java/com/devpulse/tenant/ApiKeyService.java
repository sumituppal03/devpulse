package com.devpulse.tenant;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyService {

    private static final String KEY_PREFIX = "dp_live_";
    private static final int KEY_ID_BYTE_LENGTH = 9;
    private static final int SECRET_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    /**
     * keyId: plaintext, used for fast DB lookup — proves nothing alone.
     * keySecret: the real secret — only its hash is ever stored.
     */
    public GeneratedApiKey generate() {
        String keyId = randomUrlSafeString(KEY_ID_BYTE_LENGTH);
        String keySecret = randomUrlSafeString(SECRET_BYTE_LENGTH);
        String fullPlaintextKey = KEY_PREFIX + keyId + "." + keySecret;
        return new GeneratedApiKey(keyId, keySecret, fullPlaintextKey);
    }

    public String hashSecret(String keySecret) {
        return bCryptPasswordEncoder.encode(keySecret);
    }

    public boolean secretMatches(String candidateSecret, String storedHash) {
        return bCryptPasswordEncoder.matches(candidateSecret, storedHash);
    }

    /** Splits "dp_live_{keyId}.{keySecret}" into its two parts. Null if malformed. */
    public ParsedApiKey parse(String fullPlaintextKey) {
        if (fullPlaintextKey == null || !fullPlaintextKey.startsWith(KEY_PREFIX)) {
            return null;
        }
        String withoutPrefix = fullPlaintextKey.substring(KEY_PREFIX.length());
        int dotIndex = withoutPrefix.indexOf('.');
        if (dotIndex < 0) {
            return null;
        }
        return new ParsedApiKey(
                withoutPrefix.substring(0, dotIndex),
                withoutPrefix.substring(dotIndex + 1)
        );
    }

    private String randomUrlSafeString(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public record GeneratedApiKey(String keyId, String keySecret, String fullPlaintextKey) {}

    public record ParsedApiKey(String keyId, String keySecret) {}
}