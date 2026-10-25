package com.moovup.LeakyBucketLimiter.service;

import com.moovup.LeakyBucketLimiter.config.BucketProperties;
import com.moovup.LeakyBucketLimiter.exception.UserNotFoundException;
import com.moovup.LeakyBucketLimiter.model.BucketInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

//this tests the core rate limiter logic
class BucketLimiterServiceTest {

    @Test
    void allow_shouldAllowFirstRequestForUser() {
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);

        boolean result = buckets.allow("user1", System.currentTimeMillis(), 5, 1.0);

        assertTrue(result);
        assertEquals(1.0, buckets.snapshot("user1").getLevel(), 0.01);
    }

    @Test
    void allow_shouldReturnUpdatedBucketsWithoutMutatingInput() {
        BucketLimiterService service = new BucketLimiterService();
        Map<String, BucketLimiterService.BucketState> initialBuckets = Map.of();

        BucketLimiterService.AllowResult result = service.allow(
                initialBuckets,
                "user1",
                System.currentTimeMillis(),
                defaultProperties(5, 1.0)
        );

        assertTrue(result.allowed());
        assertTrue(initialBuckets.isEmpty());
        assertEquals(1.0, result.buckets().get("user1").level(), 0.01);
    }

    @Test
    void allow_shouldReplacePastTimestampWithCurrentTime() {
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);
        long beforeRequest = System.currentTimeMillis();

        assertTrue(buckets.allow("user1", 1_000L, 5, 1.0));

        long lastUpdatedAt = (long) buckets.snapshot("user1").getLastUpdatedAt();
        assertTrue(lastUpdatedAt >= beforeRequest);
        assertTrue(lastUpdatedAt <= System.currentTimeMillis());
    }

    @Test
    void allow_shouldFillBucketAfterTwoConsecutiveCalls() {
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);
        long timestamp = System.currentTimeMillis();

        assertTrue(buckets.allow("user1", timestamp, 2, 1.0));
        BucketInfo snapshot = buckets.snapshot("user1");
        assertEquals(1.0, snapshot.getLevel(), 0.01);
        assertTrue(buckets.allow("user1", timestamp, 2, 1.0));
        snapshot = buckets.snapshot("user1");
        assertEquals(2.0, snapshot.getLevel(), 0.01);
        assertEquals(2, snapshot.getCapacity());
    }

    @Test
    void allow_shouldKeepBucketsIndependentForMultipleUsers() {
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);
        long timestamp = System.currentTimeMillis();

        assertTrue(buckets.allow("user1", timestamp, 2, 1.0));
        assertTrue(buckets.allow("user1", timestamp, 2, 1.0));
        assertFalse(buckets.allow("user1", timestamp, 2, 1.0));

        assertTrue(buckets.allow("user2", timestamp, 2, 1.0));
        assertEquals(2.0, buckets.snapshot("user1").getLevel(), 0.05);
        assertEquals(1.0, buckets.snapshot("user2").getLevel(), 0.05);
    }

    @Test
    void allow_shouldRejectWhenBucketIsFull() {
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);

        assertTrue(buckets.allow("user1", 1000L, 2, 1.0));
        assertTrue(buckets.allow("user1", 1000L, 2, 1.0));
        assertFalse(buckets.allow("user1", 1000L, 2, 1.0));
    }

    @Test
    void getBucketSnapshot_shouldReturnCurrentBucketState() {
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);

        buckets.allow("user1", System.currentTimeMillis(), 5, 2.5);
        BucketInfo snapshot = buckets.snapshot("user1");

        assertEquals(1.0, snapshot.getLevel(), 0.01);
        assertEquals(5, snapshot.getCapacity());
        assertEquals(2.5, snapshot.getLeakRate(), 0.0001);
    }

    @Test
    void getBucketSnapshot_shouldCalculateLeakBasedOnElapsedTime() throws InterruptedException {
        long lastUpdatedAt = System.currentTimeMillis();
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);
        assertTrue(buckets.allow("user1", lastUpdatedAt, 5, 0.5));

        assertEquals(1.0, buckets.snapshot("user1").getLevel(), 0.01);

        Thread.sleep(1_000L);
        BucketInfo snapshot = buckets.snapshot("user1");

        assertEquals(0.5, snapshot.getLevel(), 0.1);
        assertEquals(lastUpdatedAt, snapshot.getLastUpdatedAt(), 0.0);
    }

    @Test
    void getBucketSnapshot_shouldDrainBucketAfterLargeTimeGap() throws InterruptedException {
        long lastUpdatedAt = System.currentTimeMillis();
        BucketLimiterService service = new BucketLimiterService();
        TestBuckets buckets = new TestBuckets(service);
        assertTrue(buckets.allow("user1", lastUpdatedAt, 5, 0.5));

        Thread.sleep(2_100L);
        BucketInfo snapshot = buckets.snapshot("user1");

        assertEquals(0.0, snapshot.getLevel(), 0.0);
        assertEquals(lastUpdatedAt, snapshot.getLastUpdatedAt(), 0.0);
    }

    @Test
    void getBucketSnapshot_shouldThrowWhenUserDoesNotExist() {
        BucketLimiterService service = new BucketLimiterService();

        assertThrows(UserNotFoundException.class, () -> service.getBucketSnapshot(Map.of(), "missing-user"));
    }

    private BucketProperties defaultProperties(double capacity, double leakRate) {
        BucketProperties properties = new BucketProperties();
        properties.setCapacity(capacity);
        properties.setLeakRate(leakRate);
        return properties;
    }

    private final class TestBuckets {
        private final BucketLimiterService service;
        private Map<String, BucketLimiterService.BucketState> buckets = Map.of();

        private TestBuckets(BucketLimiterService service) {
            this.service = service;
        }

        private boolean allow(String userId, long timestamp, double capacity, double leakRate) {
            BucketLimiterService.AllowResult result = service.allow(
                    buckets,
                    userId,
                    timestamp,
                    defaultProperties(capacity, leakRate)
            );
            buckets = result.buckets();
            return result.allowed();
        }

        private BucketInfo snapshot(String userId) {
            return service.getBucketSnapshot(buckets, userId);
        }
    }
}
