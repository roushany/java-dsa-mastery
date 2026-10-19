package com.javadsamastery.controller;

import com.javadsamastery.dto.*;
import com.javadsamastery.security.LearnerTokenService;
import com.javadsamastery.security.SessionSecurityInterceptor;
import com.javadsamastery.service.SessionOrchestratorService;
import com.javadsamastery.service.exception.ForbiddenAccessException;
import com.javadsamastery.service.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionOrchestratorService sessionService;
    private final LearnerTokenService tokenService;

    public SessionController(SessionOrchestratorService sessionService, LearnerTokenService tokenService) {
        this.sessionService = sessionService;
        this.tokenService = tokenService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> startSession(
            @RequestHeader(value = SessionSecurityInterceptor.TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody StartSessionRequest request) {

        String effectiveLearnerId;

        if (token != null && !token.isBlank()) {
            if (!tokenService.validateToken(token)) {
                throw new UnauthorizedException("Invalid or expired learner token.");
            }
            String verifiedLearnerId = tokenService.extractLearnerId(token)
                    .orElseThrow(() -> new UnauthorizedException("Unable to extract learner identity from token."));

            if (request.learnerId() != null && !request.learnerId().isBlank() && !request.learnerId().equals(verifiedLearnerId)) {
                throw new ForbiddenAccessException("Learner identity mismatch: requested learnerId does not match token.");
            }
            effectiveLearnerId = verifiedLearnerId;
        } else {
            // Unauthenticated requests must not specify a client-chosen learnerId; anonymous IDs are minted exclusively by the server
            if (request.learnerId() != null && !request.learnerId().isBlank()) {
                throw new ForbiddenAccessException("Cannot specify client-chosen learnerId without a valid " + SessionSecurityInterceptor.TOKEN_HEADER + " proving possession.");
            }
            effectiveLearnerId = "learner-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }

        SessionResponse response = sessionService.startSession(request.topicId(), effectiveLearnerId);
        String outgoingToken = token != null && !token.isBlank() && tokenService.validateToken(token)
                ? token
                : tokenService.issueToken(response.learnerId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(SessionSecurityInterceptor.TOKEN_HEADER, outgoingToken)
                .body(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable("sessionId") String sessionId) {
        SessionResponse response = sessionService.getSession(sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/current-question")
    public ResponseEntity<QuestionResponse> getCurrentQuestion(@PathVariable("sessionId") String sessionId) {
        QuestionResponse response = sessionService.getCurrentQuestion(sessionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/request-hint")
    public ResponseEntity<HintResponse> requestHint(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody RequestHintRequest request) {
        HintResponse response = sessionService.requestHint(sessionId, request.attemptId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/submit-answer")
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        SubmitAnswerResponse response = sessionService.submitAnswer(
                sessionId,
                request.attemptId(),
                request.answer(),
                request.codeTestsPassed()
        );
        return ResponseEntity.ok(response);
    }
}
