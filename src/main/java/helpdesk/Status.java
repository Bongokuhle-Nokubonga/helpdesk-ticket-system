package helpdesk;

/**
 * Lifecycle of a ticket: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED.
 * A RESOLVED ticket can be re-opened; a CLOSED ticket is final.
 */
public enum Status {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
