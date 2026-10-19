package com.javadsamastery.config;

import com.javadsamastery.security.RateLimitingInterceptor;
import com.javadsamastery.security.SessionSecurityInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC configuration for CORS policy and security interceptors.
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final RateLimitingInterceptor rateLimitingInterceptor;
    private final SessionSecurityInterceptor sessionSecurityInterceptor;
    private final String[] allowedOrigins;

    public WebSecurityConfig(
            RateLimitingInterceptor rateLimitingInterceptor,
            SessionSecurityInterceptor sessionSecurityInterceptor,
            @Value("${javadsa.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins) {
        this.rateLimitingInterceptor = rateLimitingInterceptor;
        this.sessionSecurityInterceptor = sessionSecurityInterceptor;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enforce session authentication and ownership before consuming rate limits
        registry.addInterceptor(sessionSecurityInterceptor)
                .addPathPatterns("/api/v1/sessions/**")
                .order(1);

        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns("/api/**")
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Learner-Token", "Authorization")
                .exposedHeaders("X-Learner-Token", "Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
