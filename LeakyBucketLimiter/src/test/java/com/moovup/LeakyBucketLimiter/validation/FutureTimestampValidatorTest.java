package com.moovup.LeakyBucketLimiter.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FutureTimestampValidatorTest {

    private final FutureTimestampValidator validator = new FutureTimestampValidator();

    @Test
    void shouldAcceptFutureTimestamp() {
        assertTrue(validator.isValid(System.currentTimeMillis() + 60_000L, null));
    }

    @Test
    void shouldRejectCurrentOrPastTimestamp() {
        long now = System.currentTimeMillis();
        assertFalse(validator.isValid(now, null));
        assertFalse(validator.isValid(now - 5_000L, null));
    }

    @Test
    void shouldAllowNullValueForNotNullConstraintToHandle() {
        assertTrue(validator.isValid(null, null));
    }
}
