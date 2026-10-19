package com.javadsamastery.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Thread-safe token bucket rate limiter for API endpoints and AI quota protection.
 * Backed by bounded, idle-expiring storage to protect against heap exhaustion.
 */
@Service
public class RateLimiterService {

    public record AcquireResult(boolean isAcquired, long retryAfterSeconds, String message) {
        public static AcquireResult allowed() {
            return new AcquireResult(true, 0L, null);
        }

        public static AcquireResult denied(String message, long retryAfterSeconds) {
            return new AcquireResult(false, Math.max(1L, retryAfterSeconds), message);
        }
    }

    private final boolean enabled;
    private final int generalRpm;
    private final int learnerAiRpm;
    private final int sessionAiRpm;
    private final int globalAiRpm;
    private final BoundedRateLimitStorage storage;

    public RateLimiterService(
            @Value("${javadsa.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${javadsa.security.rate-limit.general-rpm:300}") int generalRpm,
            @Value("${javadsa.security.rate-limit.learner-ai-rpm:15}") int learnerAiRpm,
            @Value("${javadsa.security.rate-limit.session-ai-rpm:10}") int sessionAiRpm,
            @Value("${javadsa.security.rate-limit.global-ai-rpm:60}") int globalAiRpm,
            @Value("${javadsa.security.rate-limit.max-entries:10000}") int maxEntries,
            @Value("${javadsa.security.rate-limit.idle-timeout-seconds:600}") long idleTimeoutSeconds) {
        this.enabled = enabled;
        this.generalRpm = generalRpm;
        this.learnerAiRpm = learnerAiRpm;
        this.sessionAiRpm = sessionAiRpm;
        this.globalAiRpm = globalAiRpm;
        this.storage = new BoundedRateLimitStorage(maxEntries, idleTimeoutSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Checks rate limit for general API endpoints by client IP.
     */
    public boolean tryAcquireGeneral(String clientIp) {
        if (!enabled) {
            return true;
        }
        String key = "general:ip:" + (clientIp != null ? clientIp : "unknown");
        TokenBucket bucket = storage.getOrCreate(key, () -> new TokenBucket(generalRpm, generalRpm / 60.0));
        return bucket.tryConsume();
    }

    /**
     * Legacy single-key check for AI endpoints.
     */
    public boolean tryAcquireAi(String key) {
        if (!enabled) {
            return true;
        }
        String storageKey = key.startsWith("ai:") ? key : "ai:" + key;
        TokenBucket bucket = storage.getOrCreate(storageKey, () -> new TokenBucket(sessionAiRpm, sessionAiRpm / 60.0));
        return bucket.tryConsume();
    }

    /**
     * Checks composite rate limits for AI evaluation requests:
     * 1. Global AI provider bucket (prevents exceeding upstream account quotas).
     * 2. Per-learner AI bucket (prevents multi-session quota evasion by the same learner).
     * 3. Per-session AI bucket (prevents automated burst loops within a single session).
     *
     * Atomically validates token availability across all tiers before deducting.
     * Prevents token leakage: rejected requests never consume tokens from any bucket.
     */
    public AcquireResult tryAcquireAiComposite(String sessionId, String learnerId) {
        if (!enabled) {
            return AcquireResult.allowed();
        }

        String globalKey = "ai:global";
        TokenBucket globalBucket = storage.getOrCreate(globalKey, () -> new TokenBucket(globalAiRpm, globalAiRpm / 60.0));

        TokenBucket learnerBucket = null;
        if (learnerId != null && !learnerId.isBlank()) {
            String learnerKey = "ai:learner:" + learnerId;
            learnerBucket = storage.getOrCreate(learnerKey, () -> new TokenBucket(learnerAiRpm, learnerAiRpm / 60.0));
        }

        TokenBucket sessionBucket = null;
        if (sessionId != null && !sessionId.isBlank()) {
            String sessionKey = "ai:session:" + sessionId;
            sessionBucket = storage.getOrCreate(sessionKey, () -> new TokenBucket(sessionAiRpm, sessionAiRpm / 60.0));
        }

        // Atomic multi-bucket check-and-consume guarded by globalBucket monitor
        synchronized (globalBucket) {
            // 1. Check global AI provider quota
            if (!globalBucket.hasAvailableToken()) {
                long waitSec = globalBucket.getEstimatedWaitSeconds();
                return AcquireResult.denied("Global AI evaluation capacity reached. Please retry shortly.", waitSec);
            }

            // 2. Check learner AI quota
            if (learnerBucket != null && !learnerBucket.hasAvailableToken()) {
                long waitSec = learnerBucket.getEstimatedWaitSeconds();
                return AcquireResult.denied("Learner AI evaluation quota exceeded. Please wait before retrying.", waitSec);
            }

            // 3. Check session AI quota
            if (sessionBucket != null && !sessionBucket.hasAvailableToken()) {
                long waitSec = sessionBucket.getEstimatedWaitSeconds();
                return AcquireResult.denied("Session AI evaluation rate limit exceeded. Please wait before retrying.", waitSec);
            }

            // All tiers have available tokens: consume atomically across all tiers
            globalBucket.tryConsume();
            if (learnerBucket != null) {
                learnerBucket.tryConsume();
            }
            if (sessionBucket != null) {
                sessionBucket.tryConsume();
            }

            return AcquireResult.allowed();
        }
    }

    public long getRetryAfterSeconds(String key) {
        String storageKey = key.startsWith("ai:") || key.startsWith("general:") ? key : "ai:" + key;
        TokenBucket bucket = storage.get(storageKey);
        if (bucket == null) {
            return 5L;
        }
        return bucket.getEstimatedWaitSeconds();
    }

    public double getAvailableTokens(String key) {
        String storageKey = key.startsWith("ai:") || key.startsWith("general:") ? key : "ai:" + key;
        TokenBucket bucket = storage.get(storageKey);
        return bucket != null ? bucket.getAvailableTokens() : -1.0;
    }

    public int getStorageSize() {
        return storage.size();
    }

    public void reset() {
        storage.clear();
    }

    public static class TokenBucket {
        private final double capacity;
        private final double refillTokensPerSecond;
        private double availableTokens;
        private long lastRefillTimestampMillis;

        public TokenBucket(double capacity, double refillTokensPerSecond) {
            this.capacity = capacity;
            this.refillTokensPerSecond = refillTokensPerSecond > 0 ? refillTokensPerSecond : 1.0;
            this.availableTokens = capacity;
            this.lastRefillTimestampMillis = System.currentTimeMillis();
        }

        public synchronized boolean hasAvailableToken() {
            refill();
            return availableTokens >= 1.0;
        }

        public synchronized boolean tryConsume() {
            refill();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return true;
            }
            return false;
        }

        public synchronized void refund() {
            refill();
            this.availableTokens = Math.min(capacity, this.availableTokens + 1.0);
        }

        public synchronized long getEstimatedWaitSeconds() {
            refill();
            if (availableTokens >= 1.0) {
                return 0L;
            }
            double needed = 1.0 - availableTokens;
            double waitSec = needed / refillTokensPerSecond;
            return Math.max(1L, (long) Math.ceil(waitSec));
        }

        public synchronized double getAvailableTokens() {
            refill();
            return availableTokens;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedMillis = now - lastRefillTimestampMillis;
            if (elapsedMillis > 0) {
                double addedTokens = (elapsedMillis / 1000.0) * refillTokensPerSecond;
                this.availableTokens = Math.min(capacity, this.availableTokens + addedTokens);
                this.lastRefillTimestampMillis = now;
            }
        }
    }
}
