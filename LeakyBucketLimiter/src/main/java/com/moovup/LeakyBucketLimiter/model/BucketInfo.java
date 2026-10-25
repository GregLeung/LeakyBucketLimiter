package com.moovup.LeakyBucketLimiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BucketInfo {
    private double level;
    private int capacity;

    @JsonProperty("leak_rate")
    private double leakRate;

    @JsonProperty("last_updated_at")
    private double lastUpdatedAt;
}
