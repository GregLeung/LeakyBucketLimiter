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
public class GetBucketResponse {
    @JsonProperty("user_id")
    private String userId;

    private BucketInfo bucket;
}
