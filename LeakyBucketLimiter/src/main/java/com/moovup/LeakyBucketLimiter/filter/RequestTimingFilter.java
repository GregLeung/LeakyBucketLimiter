package com.moovup.LeakyBucketLimiter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestTimingFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;
            logger.info("trace_id={} {} {} completed with status {} in {} ms",
                    MDC.get("traceId"),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedNanos / 1_000_000.0);
        }
    }
}
