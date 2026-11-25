package com.examforge.security;

import com.examforge.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory fixed-window limiter (per client IP and endpoint) for sensitive auth endpoints.
 * Good enough for a single instance. For several instances use a shared store (e.g. Redis/Bucket4j).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/google",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email");

    private static final long WINDOW_MS = 60_000;

    private final int limitPerWindow;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int limitPerWindow, ObjectMapper objectMapper) {
        this.limitPerWindow = limitPerWindow;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > 50_000) {
            windows.values().removeIf(w -> now - w.start > WINDOW_MS);
        }
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Window window = windows.compute(key, (k, w) -> (w == null || now - w.start > WINDOW_MS) ? new Window(now) : w);

        if (window.count.incrementAndGet() > limitPerWindow) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - window.start)) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(429, "RATE_LIMITED",
                    "Too many attempts. Please wait " + retryAfter + " seconds and try again.",
                    request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
