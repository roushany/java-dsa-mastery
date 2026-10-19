package com.javadsamastery.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for issuing and validating anonymous learner capability tokens.
 *
 * <h3>Threat Model &amp; Token Lifecycle</h3>
 * <ul>
 *   <li><b>Objective:</b> Establish a lightweight cryptographic binding between a client and their learner identity
 *       without requiring registration/passwords, preventing learner impersonation and cross-learner session access.</li>
 *   <li><b>Format:</b> {@code <learnerId>.<timestampMs>.<base64url-hmac>}</li>
 *   <li><b>Integrity:</b> HMAC-SHA256 ensures tokens cannot be forged or tampered with by clients without the server secret.</li>
 *   <li><b>Replay &amp; Expiration:</b> Tokens carry an epoch timestamp. Tokens older than {@code validityDays} (default 30 days)
 *       or with future timestamps are rejected.</li>
 *   <li><b>Timing Defense:</b> Signatures are verified using {@link MessageDigest#isEqual(byte[], byte[])} for constant-time comparison.</li>
 *   <li><b>Production Secret Guard:</b> Production startup aborts if the secret is absent, too short (&lt; 32 chars), or uses a dev fallback.</li>
 * </ul>
 */
@Service
public class LearnerTokenService {

    public static final String DEFAULT_DEV_SECRET = "javadsa-mastery-default-secret-key-32chars-min!";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secretKeyBytes;
    private final long validityMillis;

    public LearnerTokenService(
            @Value("${javadsa.security.token.secret:" + DEFAULT_DEV_SECRET + "}") String secretKey,
            @Value("${javadsa.security.token.validity-days:30}") long validityDays,
            @Value("${spring.profiles.active:default}") String activeProfiles) {

        boolean isProd = activeProfiles != null && (activeProfiles.contains("prod") || activeProfiles.contains("production"));
        if (isProd) {
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("FATAL CONFIGURATION ERROR: JAVADSA_SECURITY_TOKEN_SECRET environment variable is mandatory in production profile.");
            }
            if (DEFAULT_DEV_SECRET.equals(secretKey) || secretKey.toLowerCase().contains("default") || secretKey.toLowerCase().contains("fallback")) {
                throw new IllegalStateException("FATAL CONFIGURATION ERROR: JAVADSA_SECURITY_TOKEN_SECRET must not use a known development fallback or insecure default in production.");
            }
            if (secretKey.length() < 32) {
                throw new IllegalStateException("FATAL CONFIGURATION ERROR: JAVADSA_SECURITY_TOKEN_SECRET must be at least 32 characters in production.");
            }
        }

        if (secretKey == null || secretKey.length() < 16) {
            throw new IllegalArgumentException("Token secret must be at least 16 characters.");
        }

        this.secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.validityMillis = validityDays * 24L * 60L * 60L * 1000L;
    }

    /**
     * Issues a signed HMAC-SHA256 token for a learner ID.
     */
    public String issueToken(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            throw new IllegalArgumentException("learnerId cannot be null or blank");
        }
        long now = Instant.now().toEpochMilli();
        String payload = learnerId + "." + now;
        byte[] signature = sign(payload);
        String sigEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return payload + "." + sigEncoded;
    }

    /**
     * Validates token format, expiration, and cryptographic signature.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        String learnerId = parts[0];
        if (learnerId.isBlank()) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        long now = Instant.now().toEpochMilli();
        // Disallow expired tokens or tokens with timestamps far in the future (> 1 minute clock skew)
        if (now - timestamp > validityMillis || timestamp > now + 60_000L) {
            return false;
        }

        byte[] providedSig;
        try {
            providedSig = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String payload = learnerId + "." + parts[1];
        byte[] expectedSig = sign(payload);

        return MessageDigest.isEqual(expectedSig, providedSig);
    }

    /**
     * Extracts and returns the verified learner ID if the token is valid.
     */
    public Optional<String> extractLearnerId(String token) {
        if (!validateToken(token)) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        return Optional.of(parts[0]);
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
