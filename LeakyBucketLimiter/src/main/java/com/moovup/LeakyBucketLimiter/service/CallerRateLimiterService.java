package com.moovup.LeakyBucketLimiter.service;

import com.moovup.LeakyBucketLimiter.config.RateLimitProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CallerRateLimiterService {
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowNanos;

    public CallerRateLimiterService(RateLimitProperties properties) {
        if (properties.getMaxRequests() < 1 || properties.getWindowMs() < 1) {
            throw new IllegalArgumentException("Rate limit must have a positive request count and window");
        }

        this.maxRequests = properties.getMaxRequests();
        this.windowNanos = Math.multiplyExact(properties.getWindowMs(), 1_000_000L);
    }

    public boolean tryAcquire(String callerId) {
        long now = System.nanoTime();
        Window window = windows.computeIfAbsent(callerId, key -> new Window(now));

        synchronized (window) {
            if (now - window.startedAtNanos >= windowNanos) {
                window.startedAtNanos = now;
                window.requestCount = 0;
            }

            if (window.requestCount >= maxRequests) {
                return false;
            }

            window.requestCount++;
            return true;
        }
    }

    private static final class Window {
        private long startedAtNanos;
        private int requestCount;

        private Window(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }
    }
}
