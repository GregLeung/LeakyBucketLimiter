package com.moovup.LeakyBucketLimiter.model;

import com.moovup.LeakyBucketLimiter.validation.ValidFutureTimestamp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllowRequest {
    @NotNull(message = "Timestamp is required")
    @ValidFutureTimestamp(message = "Timestamp must be in the future.")
    private Long timestamp;

    @NotBlank(message = "userId is required")
    private String userId;
}
