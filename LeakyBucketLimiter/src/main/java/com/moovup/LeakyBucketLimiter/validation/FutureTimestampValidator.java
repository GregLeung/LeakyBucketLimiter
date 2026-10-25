package com.moovup.LeakyBucketLimiter.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;

public class FutureTimestampValidator implements ConstraintValidator<ValidFutureTimestamp, Long> {

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        long now = Instant.now().toEpochMilli();
        return value > now;
    }
}
