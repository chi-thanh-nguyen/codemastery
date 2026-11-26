package com.codemastery.modules.assessment.application.scheduler;

import com.codemastery.modules.assessment.application.service.AttemptService;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.codemastery.common.config.AppConfig.QuizExpirySettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CT04.expiry background auto-submitter (ADR007 §§3.2, 10).
 *
 * <p>Pages due unsubmitted attempts in ascending {@code (expires_at, attempt UUID text)} order using
 * keyset pagination so a failed earliest row never starves later candidates. Each candidate runs in
 * its own transaction inside {@link AttemptService#finalizeExpired(UUID)}.
 */
@Component
@ConditionalOnProperty(name = "app.quiz.auto-submit.enabled", havingValue = "true")
public class AttemptAutoSubmitScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttemptAutoSubmitScheduler.class);

    private final AttemptRepository attempts;
    private final AttemptService attemptService;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public AttemptAutoSubmitScheduler(
            AttemptRepository attempts,
            AttemptService attemptService,
            QuizExpirySettings settings) {
        this(attempts, attemptService, settings.delayMs(), settings.batchSize(), Clock.systemUTC());
    }

    public AttemptAutoSubmitScheduler(
            AttemptRepository attempts,
            AttemptService attemptService,
            int delayMs,
            int batchSize,
            Clock clock) {
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.attemptService = Objects.requireNonNull(attemptService, "attemptService");
        if (delayMs < 1) {
            throw new IllegalArgumentException("app.quiz.auto-submit.delay-ms must be >= 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.quiz.auto-submit.batch-size must be >= 1");
        }
        this.batchSize = batchSize;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${app.quiz.auto-submit.delay-ms}")
    public void scheduledRun() {
        runPass();
    }

    /** Executes one full keyset-paged pass over currently overdue unsubmitted attempts. */
    public int runPass() {
        Instant passNow = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant cursorExpiresAt = null;
        UUID cursorAttemptId = null;
        int processed = 0;

        while (true) {
            List<Attempt> batch = attempts.findDueUnsubmitted(passNow, cursorExpiresAt, cursorAttemptId, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (Attempt candidate : batch) {
                cursorExpiresAt = candidate.expires_at();
                cursorAttemptId = candidate.id();
                try {
                    attemptService.finalizeExpired(candidate.id());
                    processed++;
                } catch (RuntimeException ex) {
                    log.error("Failed to finalize overdue attemptId={}; failureType={}",
                            candidate.id(), ex.getClass().getSimpleName());
                }
            }
            if (batch.size() < batchSize) {
                break;
            }
        }
        return processed;
    }
}
