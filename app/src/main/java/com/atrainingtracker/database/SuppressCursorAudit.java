package com.atrainingtracker.database;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Suppresses static cursor safety audits for legacy or framework-interfacing methods where
 * automated try-with-resources enforcement cannot be applied.
 *
 * <p>Requires an active Jira ticket ID, clear justification reason, and an ISO-8601 expiration date (YYYY-MM-DD).
 * The build and CI/CD verification will fail if an expired suppression is encountered.</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface SuppressCursorAudit {
    /**
     * The Jira ticket tracking this technical debt (must match ATT-\d+).
     */
    String ticketId();

    /**
     * Justification explaining why try-with-resources cannot be used.
     */
    String reason();

    /**
     * ISO-8601 expiration date (YYYY-MM-DD) after which this suppression is invalid and fails CI.
     */
    String expiryDate();
}
