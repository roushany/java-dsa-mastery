package com.javadsamastery.security;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe, bounded, and idle-expiring storage for rate limiter token buckets.
 * Prevents heap exhaustion (OutOfMemoryError) from unconstrained key allocations.
 */
public class BoundedRateLimitStorage {

    private final int maxCapacity;
    private final long idleTimeoutMillis;
    private final Map<String, Entry> map;
    private final ReentrantLock lock = new ReentrantLock();

    public static class Entry {
        final RateLimiterService.TokenBucket bucket;
        long lastAccessTime;

        Entry(RateLimiterService.TokenBucket bucket, long now) {
            this.bucket = bucket;
            this.lastAccessTime = now;
        }

        public RateLimiterService.TokenBucket getBucket() {
            return bucket;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }

    public BoundedRateLimitStorage(int maxCapacity, long idleTimeoutSeconds) {
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 10_000;
        this.idleTimeoutMillis = idleTimeoutSeconds > 0 ? idleTimeoutSeconds * 1000L : 600_000L;
        // LinkedHashMap in access-order mode (true) for LRU eviction
        this.map = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * Retrieves an existing bucket or creates a new one using the provided supplier.
     * Updates the last access timestamp and enforces capacity limits and idle expiration.
     */
    public RateLimiterService.TokenBucket getOrCreate(String key, Supplier<RateLimiterService.TokenBucket> supplier) {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            Entry entry = map.get(key);

            if (entry != null) {
                if (now - entry.lastAccessTime > idleTimeoutMillis) {
                    // Entry has expired due to idle timeout; refresh with a new bucket
                    entry = new Entry(supplier.get(), now);
                    map.put(key, entry);
                } else {
                    entry.lastAccessTime = now;
                }
                return entry.bucket;
            }

            // Evict expired entries or LRU eldest if at capacity
            if (map.size() >= maxCapacity) {
                evictEldestOrExpired(now);
            }

            Entry newEntry = new Entry(supplier.get(), now);
            map.put(key, newEntry);
            return newEntry.bucket;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves a bucket without creating a new one if missing.
     */
    public RateLimiterService.TokenBucket get(String key) {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            Entry entry = map.get(key);
            if (entry == null) {
                return null;
            }
            if (now - entry.lastAccessTime > idleTimeoutMillis) {
                map.remove(key);
                return null;
            }
            entry.lastAccessTime = now;
            return entry.bucket;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

    private void evictEldestOrExpired(long now) {
        // LinkedHashMap in access-order mode maintains the oldest accessed entry at the head
        Iterator<Map.Entry<String, Entry>> iterator = map.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
