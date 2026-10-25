package com.moovup.LeakyBucketLimiter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "bucket")
public class BucketProperties {
    private double capacity;
    private double leakRate;

}
