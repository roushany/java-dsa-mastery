package com.javadsamastery.service;

import com.javadsamastery.domain.ports.AttemptRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Background daemon that sweeps expired learning sessions and orphaned unconsumed attempts.
 * Operates idempotently using database conditional atomic updates.
 */
@Service
@ConditionalOnProperty(name = "javadsa.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionReaperService {

    private static final Logger log = LoggerFactory.getLogger(SessionReaperService.class);

    private final Optional<SessionRepositoryPort> sessionRepository;
    private final Optional<AttemptRepositoryPort> attemptRepository;
    private final SessionOrchestratorService sessionOrchestratorService;

    public SessionReaperService(
            Optional<SessionRepositoryPort> sessionRepository,
            Optional<AttemptRepositoryPort> attemptRepository,
            SessionOrchestratorService sessionOrchestratorService) {
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.sessionOrchestratorService = sessionOrchestratorService;
    }

    public record ReaperResult(
            int expiredSessionsCount,
            int expiredAttemptsCount,
            int cachedSessionsEvicted
    ) {}

    @Scheduled(cron = "${javadsa.cleanup.reaper-cron:0 0 * * * *}")
    public void scheduledReap() {
        reapExpiredSessions();
    }

    public ReaperResult reapExpiredSessions() {
        return reapExpiredSessions(Instant.now());
    }

    public ReaperResult reapExpiredSessions(Instant now) {
        int expiredAttempts = 0;
        int expiredSessions = 0;

        if (attemptRepository.isPresent() && sessionRepository.isPresent()) {
            // 1. Atomically expire unconsumed ISSUED attempts belonging to expired sessions
            expiredAttempts = attemptRepository.get().expireIssuedAttemptsForExpiredSessions(now);

            // 2. Atomically mark expired sessions as completed
            expiredSessions = sessionRepository.get().expireSessions(now);
        }

        // 3. Reclaim any expired sessions from the in-memory Caffeine cache
        int cachedEvicted = sessionOrchestratorService.reapExpiredCachedSessions(now);

        log.info("Session reaper executed at {}: {} sessions expired, {} attempts expired, {} cached sessions evicted",
                now, expiredSessions, expiredAttempts, cachedEvicted);

        return new ReaperResult(expiredSessions, expiredAttempts, cachedEvicted);
    }
}
