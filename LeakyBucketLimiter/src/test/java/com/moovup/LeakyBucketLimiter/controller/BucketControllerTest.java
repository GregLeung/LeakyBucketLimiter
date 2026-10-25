package com.moovup.LeakyBucketLimiter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moovup.LeakyBucketLimiter.advice.ApiExceptionHandler;
import com.moovup.LeakyBucketLimiter.config.BucketProperties;
import com.moovup.LeakyBucketLimiter.config.RateLimitProperties;
import com.moovup.LeakyBucketLimiter.exception.UserNotFoundException;
import com.moovup.LeakyBucketLimiter.model.AllowRequest;
import com.moovup.LeakyBucketLimiter.model.BucketInfo;
import com.moovup.LeakyBucketLimiter.service.CallerRateLimiterService;
import com.moovup.LeakyBucketLimiter.service.BucketLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = BucketController.class, properties = {
        "rate-limit.max-requests=2",
        "rate-limit.window-ms=1000",
        "bucket.capacity=5",
        "bucket.leak-rate=1.0"
})
@EnableConfigurationProperties({RateLimitProperties.class, BucketProperties.class})
@Import({ApiExceptionHandler.class, CallerRateLimiterService.class})
class BucketControllerTest {

    private static final String CALLER_HEADER = "X-Caller-Id";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BucketLimiterService bucketLimiterService;

    @Test
    void postAllow_shouldReturnAllowedResponse() throws Exception {
        AllowRequest request = new AllowRequest(System.currentTimeMillis() + 60_000L, "user1");
        BucketInfo bucket = new BucketInfo(1.0, 5, 1.0, 1710000000.0);

        when(bucketLimiterService.allow(
                anyMap(),
                eq("user1"),
                eq(request.getTimestamp()),
                any(BucketProperties.class)
        )).thenReturn(new BucketLimiterService.AllowResult(true, Map.of()));
        when(bucketLimiterService.getBucketSnapshot(anyMap(), eq("user1"))).thenReturn(bucket);

        mockMvc.perform(post("/allow")
                        .header(CALLER_HEADER, "post-allow-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.user_id").value("user1"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.bucket.level").value(1.0))
                .andExpect(jsonPath("$.bucket.capacity").value(5));
    }

    @Test
    void postAllow_shouldReturnReasonWhenBucketCapacityIsExceeded() throws Exception {
        AllowRequest request = new AllowRequest(System.currentTimeMillis() + 60_000L, "user1");
        BucketInfo bucket = new BucketInfo(5.0, 5, 1.0, 1710000000.0);

        when(bucketLimiterService.allow(
                anyMap(),
                eq("user1"),
                eq(request.getTimestamp()),
                any(BucketProperties.class)
        )).thenReturn(new BucketLimiterService.AllowResult(false, Map.of()));
        when(bucketLimiterService.getBucketSnapshot(anyMap(), eq("user1"))).thenReturn(bucket);

        mockMvc.perform(post("/allow")
                        .header(CALLER_HEADER, "post-allow-capacity-exceeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.user_id").value("user1"))
                .andExpect(jsonPath("$.reason").value("bucket_capacity_exceeded"))
                .andExpect(jsonPath("$.bucket.level").value(5.0));
    }

    @Test
    void postAllow_shouldAcceptFutureTimestamp() throws Exception {
        AllowRequest request = new AllowRequest(System.currentTimeMillis() + 60_000L, "user1");
        BucketInfo bucket = new BucketInfo(1.0, 5, 1.0, request.getTimestamp());
        when(bucketLimiterService.allow(
                anyMap(),
                eq("user1"),
                eq(request.getTimestamp()),
                any(BucketProperties.class)
        )).thenReturn(new BucketLimiterService.AllowResult(true, Map.of()));
        when(bucketLimiterService.getBucketSnapshot(anyMap(), eq("user1"))).thenReturn(bucket);

        mockMvc.perform(post("/allow")
                        .header(CALLER_HEADER, "post-allow-future")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void postAllow_shouldRejectPastTimestamp() throws Exception {
        AllowRequest request = new AllowRequest(System.currentTimeMillis() - 5_000L, "user1");

        mockMvc.perform(post("/allow")
                        .header(CALLER_HEADER, "post-allow-past")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postAllow_shouldRejectBlankUserId() throws Exception {
        AllowRequest request = new AllowRequest(System.currentTimeMillis() - 60_000L, " ");

        mockMvc.perform(post("/allow")
                        .header(CALLER_HEADER, "post-allow-blank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBucket_shouldReturnBucketSnapshot() throws Exception {
        BucketInfo bucket = new BucketInfo(2.0, 5, 1.0, 1710000000.0);
        when(bucketLimiterService.getBucketSnapshot(anyMap(), eq("user1"))).thenReturn(bucket);

        mockMvc.perform(get("/users/user1/bucket")
                        .header(CALLER_HEADER, "get-bucket-success")
                        .header("X-Trace-Id", "trace-provided"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-provided"))
                .andExpect(jsonPath("$.user_id").value("user1"))
                .andExpect(jsonPath("$.bucket.level").value(2.0))
                .andExpect(jsonPath("$.bucket.capacity").value(5))
                .andExpect(jsonPath("$.bucket.leak_rate").value(1.0))
                .andExpect(jsonPath("$.bucket.last_updated_at").value(1710000000.0));
    }

    @Test
    void getBucket_shouldReturnUserWithNullBucketWhenUserDoesNotExist() throws Exception {
        when(bucketLimiterService.getBucketSnapshot(anyMap(), eq("missing-user")))
                .thenThrow(new UserNotFoundException("User not found: missing-user"));

        mockMvc.perform(get("/users/missing-user/bucket")
                        .header(CALLER_HEADER, "get-bucket-missing"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.user_id").value("missing-user"))
                .andExpect(jsonPath("$.bucket").value(nullValue()));
    }

    @Test
    void getBucket_shouldRejectBlankUserIdInPath() throws Exception {
        mockMvc.perform(get("/users/ /bucket")
                        .header(CALLER_HEADER, "get-bucket-blank"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requests_shouldBeRateLimitedPerCaller() throws Exception {
        mockMvc.perform(get("/users/user1/bucket").header(CALLER_HEADER, "limited-caller"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/user1/bucket").header(CALLER_HEADER, "limited-caller"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/user1/bucket").header(CALLER_HEADER, "limited-caller"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"));

        mockMvc.perform(get("/users/user1/bucket").header(CALLER_HEADER, "different-caller"))
                .andExpect(status().isOk());
    }

    @Test
    void requests_shouldRequireCallerIdentityHeader() throws Exception {
        mockMvc.perform(get("/users/user1/bucket"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.error").value("Missing caller identity header"));
    }

    @Test
    void requests_shouldGenerateTraceIdWhenMissing() throws Exception {
        mockMvc.perform(get("/users/user1/bucket").header(CALLER_HEADER, "trace-id-generated"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.matchesPattern(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                )));
    }
}
