package com.javadsamastery.security;

import com.javadsamastery.service.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spring Web HandlerInterceptor to enforce client IP and composite endpoint rate limits.
 * Validates trusted reverse proxy provenance before honoring X-Forwarded-For headers,
 * traversing proxy chains right-to-left to prevent header-prepend spoofing.
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Pattern AI_ENDPOINT_PATTERN = Pattern.compile("^/api/v1/sessions/([^/]+)/(submit-answer|request-hint)$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
    );
    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

    private final RateLimiterService rateLimiterService;
    private final Set<String> trustedProxies;

    public RateLimitingInterceptor(
            RateLimiterService rateLimiterService,
            @Value("${javadsa.security.proxy.trusted-proxies:}") String trustedProxiesConfig) {
        this.rateLimiterService = rateLimiterService;
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .map(RateLimitingInterceptor::sanitizeIp)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimiterService.isEnabled()) {
            return true;
        }

        String ip = extractClientIp(request);
        String rawUri = request.getRequestURI();
        String normalizedUri = normalizeUri(rawUri);

        // 1. General endpoint rate limit (by validated client IP)
        if (!rateLimiterService.tryAcquireGeneral(ip)) {
            long retryAfter = rateLimiterService.getRetryAfterSeconds("general:ip:" + ip);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            throw new RateLimitExceededException("API rate limit exceeded. Please wait before retrying.", retryAfter);
        }

        // 2. Dedicated composite AI endpoint rate limits (submit-answer and request-hint)
        Matcher aiMatcher = AI_ENDPOINT_PATTERN.matcher(normalizedUri);
        if (aiMatcher.matches()) {
            String sessionId = aiMatcher.group(1);
            String learnerId = (String) request.getAttribute("validatedLearnerId");

            RateLimiterService.AcquireResult result = rateLimiterService.tryAcquireAiComposite(sessionId, learnerId);
            if (!result.isAcquired()) {
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
                throw new RateLimitExceededException(result.message(), result.retryAfterSeconds());
            }
        }

        return true;
    }

    /**
     * Normalizes request URI by stripping matrix parameters (;...) and trailing slashes.
     */
    public static String normalizeUri(String uri) {
        if (uri == null) {
            return "";
        }
        int semicolonIndex = uri.indexOf(';');
        if (semicolonIndex != -1) {
            uri = uri.substring(0, semicolonIndex);
        }
        while (uri.endsWith("/") && uri.length() > 1) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    /**
     * Extracts client IP. Only honors X-Forwarded-For if the direct socket connection is from a trusted proxy.
     * Evaluates forwarded chains right-to-left to prevent attacker prepend spoofing.
     */
    public String extractClientIp(HttpServletRequest request) {
        String remoteAddr = sanitizeIp(request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1");

        if (trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteAddr;
        }

        String[] hops = xForwardedFor.split(",");
        String clientCandidate = null;

        // Traverse right-to-left (most recently appended hop backwards)
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = sanitizeIp(hops[i]);
            if (!isValidIp(hop)) {
                continue;
            }
            if (trustedProxies.contains(hop)) {
                continue; // Intermediate trusted proxy
            }
            // First untrusted IP from the right is the authentic client IP
            clientCandidate = hop;
            break;
        }

        return clientCandidate != null ? clientCandidate : remoteAddr;
    }

    static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String clean = sanitizeIp(ip);
        if (IPV4_PATTERN.matcher(clean).matches()) {
            return true;
        }
        return clean.contains(":") && IPV6_PATTERN.matcher(clean).matches();
    }

    static String sanitizeIp(String ip) {
        if (ip == null) {
            return "127.0.0.1";
        }
        String clean = ip.trim();
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean;
    }
}
