package com.moovup.LeakyBucketLimiter.service;

import com.moovup.LeakyBucketLimiter.config.BucketProperties;
import com.moovup.LeakyBucketLimiter.exception.UserNotFoundException;
import com.moovup.LeakyBucketLimiter.model.BucketInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class BucketLimiterService {

    public AllowResult allow(
            Map<String, BucketState> buckets,
            String userId,
            long timestamp,
            BucketProperties bucketProperties
    ) {
        long effectiveTimestamp = Math.max(timestamp, System.currentTimeMillis());
        BucketState bucket = buckets.getOrDefault(
                userId,
                new BucketState(bucketProperties.getCapacity(), bucketProperties.getLeakRate(), effectiveTimestamp)
        );

        long eventTime = Math.max(effectiveTimestamp, bucket.lastUpdateTimestamp);
        double elapsedSeconds = Math.max(0.0, (eventTime - bucket.lastUpdateTimestamp) / 1000.0);

        double leakedLevel = Math.max(0.0, bucket.level - elapsedSeconds * bucket.leakRate);
        BucketState updatedBucket = new BucketState(
                bucket.capacity,
                bucket.leakRate,
                leakedLevel,
                eventTime
        );
        Map<String, BucketState> updatedBuckets = new HashMap<>(buckets);

        if (leakedLevel + 1.0 > bucket.capacity) {
            updatedBuckets.put(userId, updatedBucket);
            return new AllowResult(false, Map.copyOf(updatedBuckets));
        }

        updatedBuckets.put(userId, new BucketState(
                bucket.capacity,
                bucket.leakRate,
                leakedLevel + 1.0,
                eventTime
        ));
        return new AllowResult(true, Map.copyOf(updatedBuckets));
    }

    public BucketInfo getBucketSnapshot(Map<String, BucketState> buckets, String userId) {
        BucketState state = buckets.get(userId);
        if (state == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        double elapsedSeconds = Math.max(0.0, (System.currentTimeMillis() - state.lastUpdateTimestamp) / 1000.0);
        double currentLevel = Math.max(0.0, state.level - elapsedSeconds * state.leakRate);
        return new BucketInfo(currentLevel, (int) state.capacity, state.leakRate, state.lastUpdateTimestamp);
    }

    public record AllowResult(boolean allowed, Map<String, BucketState> buckets) {
    }

    public record BucketState(double capacity, double leakRate, double level, long lastUpdateTimestamp) {
        public BucketState(double capacity, double leakRate, long timestamp) {
            this(capacity, leakRate, 0.0, timestamp);
        }
    }
}
