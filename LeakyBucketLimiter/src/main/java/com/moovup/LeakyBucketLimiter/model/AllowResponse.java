package com.moovup.LeakyBucketLimiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllowResponse {
    private boolean allowed;

    @JsonProperty("user_id")
    private String userId;

    private BucketInfo bucket;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reason;
}
