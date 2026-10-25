package com.moovup.LeakyBucketLimiter.exception;

public class BucketOverflowException extends RuntimeException {
    public BucketOverflowException(String message) {
        super(message);
    }
}
