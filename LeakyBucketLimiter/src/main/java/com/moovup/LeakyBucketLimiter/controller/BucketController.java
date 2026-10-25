package com.moovup.LeakyBucketLimiter.controller;

import com.moovup.LeakyBucketLimiter.model.AllowRequest;
import com.moovup.LeakyBucketLimiter.model.AllowResponse;
import com.moovup.LeakyBucketLimiter.model.BucketInfo;
import com.moovup.LeakyBucketLimiter.model.GetBucketResponse;
import com.moovup.LeakyBucketLimiter.exception.UserNotFoundException;
import com.moovup.LeakyBucketLimiter.config.BucketProperties;
import com.moovup.LeakyBucketLimiter.service.BucketLimiterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Validated
public class BucketController {

    private final BucketLimiterService bucketLimiterService;
    private final BucketProperties bucketProperties;
    private Map<String, BucketLimiterService.BucketState> buckets = Map.of();

    public BucketController(BucketLimiterService bucketLimiterService, BucketProperties bucketProperties) {
        this.bucketLimiterService = bucketLimiterService;
        this.bucketProperties = bucketProperties;
    }

    @PostMapping("/allow")
    public ResponseEntity<AllowResponse> allow(@Valid @RequestBody AllowRequest requestBody) {
        BucketLimiterService.AllowResult result = bucketLimiterService.allow(
                buckets,
                requestBody.getUserId(),
                requestBody.getTimestamp(),
                bucketProperties
        );
        buckets = result.buckets();
        BucketInfo bucket = bucketLimiterService.getBucketSnapshot(buckets, requestBody.getUserId());
        String reason = result.allowed() ? null : "bucket_capacity_exceeded";
        AllowResponse response = new AllowResponse(result.allowed(), requestBody.getUserId(), bucket, reason);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{user_id}/bucket")
    public ResponseEntity<GetBucketResponse> getBucket(@PathVariable("user_id") @NotBlank String userId) {
        try {
            return ResponseEntity.ok(new GetBucketResponse(
                    userId,
                    bucketLimiterService.getBucketSnapshot(buckets, userId)
            ));
        } catch (UserNotFoundException exception) {
            return ResponseEntity.unprocessableContent().body(new GetBucketResponse(userId, null));
        }
    }
}
