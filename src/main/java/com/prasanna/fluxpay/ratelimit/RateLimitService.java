package com.prasanna.fluxpay.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed-window rate limiter. Redis is used across instances; a local fallback keeps dev usable without Redis. */
@Service
public class RateLimitService {
    private final StringRedisTemplate redis;
    private final int authLimit;
    private final int paymentLimit;
    private final ConcurrentHashMap<String, AtomicInteger> fallback = new ConcurrentHashMap<>();

    public RateLimitService(StringRedisTemplate redis,
            @Value("${app.rate-limit.auth-per-minute:10}") int authLimit,
            @Value("${app.rate-limit.payment-per-minute:30}") int paymentLimit) {
        this.redis = redis;
        this.authLimit = Math.max(1, authLimit);
        this.paymentLimit = Math.max(1, paymentLimit);
    }

    public void checkAuth(HttpServletRequest request, String action) {
        check(request, "auth-" + action, authLimit);
    }

    public void checkPaymentCreate(HttpServletRequest request) {
        check(request, "payment-create", paymentLimit);
    }

    private void check(HttpServletRequest request, String bucket, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        String identity = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String key = "fluxpay:rate:" + bucket + ":" + identity + ":" + minute;
        long count;
        try {
            Long incremented = redis.opsForValue().increment(key);
            count = incremented == null ? 1 : incremented;
            if (count == 1) redis.expire(key, Duration.ofMinutes(2));
        } catch (DataAccessException | IllegalStateException unavailable) {
            // Local-only fallback is not shared across replicas; production should run Redis.
            count = fallback.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            if (fallback.size() > 10_000) fallback.clear();
        }
        if (count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again in the next minute.");
        }
    }
}
