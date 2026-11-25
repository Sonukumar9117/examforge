package com.examforge.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Auto-submits attempts whose time ran out while the student was away (closed tab, lost connection).
 * Each attempt is finalised in its own transaction with a row lock, so this never clashes with a submit.
 */
@Component
@RequiredArgsConstructor
public class AttemptExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryJob.class);
    private final AttemptService attempts;

    @Scheduled(fixedDelayString = "${app.exam.expiry-sweep-ms:30000}", initialDelay = 20_000)
    public void sweep() {
        for (Long id : attempts.expiredAttemptIds()) {
            try {
                attempts.expire(id);
            } catch (Exception e) {
                log.warn("Could not auto-submit attempt {}: {}", id, e.getMessage());
            }
        }
    }
}
