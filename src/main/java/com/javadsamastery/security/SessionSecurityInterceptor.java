package com.javadsamastery.security;

import com.javadsamastery.dto.SessionResponse;
import com.javadsamastery.service.SessionOrchestratorService;
import com.javadsamastery.service.exception.ForbiddenAccessException;
import com.javadsamastery.service.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Spring Web HandlerInterceptor to enforce learner identity and session ownership.
 * Prevents unauthorized or cross-learner access to session and attempt resources.
 * Defaults strict ownership to true.
 */
@Component
public class SessionSecurityInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Learner-Token";

    private final LearnerTokenService tokenService;
    private final SessionOrchestratorService sessionService;
    private final boolean strictOwnership;

    public SessionSecurityInterceptor(
            LearnerTokenService tokenService,
            SessionOrchestratorService sessionService,
            @Value("${javadsa.security.auth.strict-ownership:true}") boolean strictOwnership) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.strictOwnership = strictOwnership;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables == null || !pathVariables.containsKey("sessionId")) {
            return true;
        }

        String sessionId = pathVariables.get("sessionId");
        String token = request.getHeader(TOKEN_HEADER);

        if (token == null || token.isBlank()) {
            if (strictOwnership) {
                throw new UnauthorizedException("Missing required " + TOKEN_HEADER + " header.");
            }
            return true;
        }

        if (!tokenService.validateToken(token)) {
            throw new UnauthorizedException("Invalid or expired learner token.");
        }

        String callerLearnerId = tokenService.extractLearnerId(token)
                .orElseThrow(() -> new UnauthorizedException("Unable to extract learner identity from token."));

        SessionResponse session = sessionService.getSession(sessionId);
        if (!session.learnerId().equals(callerLearnerId)) {
            throw new ForbiddenAccessException("Cross-learner access denied: Learner " + callerLearnerId + " does not own session " + sessionId + ".");
        }

        // Store validated learner identity on request for downstream interceptors/services
        request.setAttribute("validatedLearnerId", callerLearnerId);

        return true;
    }
}
