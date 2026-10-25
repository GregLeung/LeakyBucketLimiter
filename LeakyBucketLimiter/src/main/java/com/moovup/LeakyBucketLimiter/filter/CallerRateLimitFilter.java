package com.moovup.LeakyBucketLimiter.filter;

import com.moovup.LeakyBucketLimiter.config.RateLimitProperties;
import com.moovup.LeakyBucketLimiter.service.CallerRateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CallerRateLimitFilter extends OncePerRequestFilter {
    private final CallerRateLimiterService rateLimiter;
    private final String callerHeaderName;

    public CallerRateLimitFilter(CallerRateLimiterService rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        if (properties.getHeaderName() == null || properties.getHeaderName().isBlank()) {
            throw new IllegalArgumentException("Rate limit caller header name must not be blank");
        }
        this.callerHeaderName = properties.getHeaderName();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String callerId = request.getHeader(callerHeaderName);
        if (callerId == null || callerId.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing caller identity header");
            return;
        }

        if (!rateLimiter.tryAcquire(callerId)) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
