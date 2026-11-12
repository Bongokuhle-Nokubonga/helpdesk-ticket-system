package helpdesk;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single IT support request.
 */
public class Ticket {
    private final int id;
    private final String title;
    private final String description;
    private final String requester;
    private final Category category;
    private final Priority priority;
    private final LocalDateTime createdAt;

    private Status status;
    private String assignee;
    private LocalDateTime resolvedAt;
    private final List<String> notes;

    public Ticket(int id, String title, String description, String requester,
                  Category category, Priority priority, LocalDateTime createdAt) {
        this(id, title, description, requester, category, priority, createdAt,
                Status.OPEN, "", null, new ArrayList<>());
    }

    /** Full constructor, used when loading tickets from storage. */
    public Ticket(int id, String title, String description, String requester,
                  Category category, Priority priority, LocalDateTime createdAt,
                  Status status, String assignee, LocalDateTime resolvedAt, List<String> notes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.requester = requester;
        this.category = category;
        this.priority = priority;
        this.createdAt = createdAt;
        this.status = status;
        this.assignee = assignee == null ? "" : assignee;
        this.resolvedAt = resolvedAt;
        this.notes = new ArrayList<>(notes);
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRequester() { return requester; }
    public Category getCategory() { return category; }
    public Priority getPriority() { return priority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public String getAssignee() { return assignee; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public List<String> getNotes() { return Collections.unmodifiableList(notes); }

    void setStatus(Status status) { this.status = status; }
    void setAssignee(String assignee) { this.assignee = assignee; }
    void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    void addNote(String note) { notes.add(note); }

    public boolean isResolved() {
        return status == Status.RESOLVED || status == Status.CLOSED;
    }

    /** The time by which this ticket should be resolved. */
    public LocalDateTime slaDeadline() {
        return createdAt.plusHours(priority.slaHours());
    }

    /** True if the ticket is still unresolved after its SLA deadline. */
    public boolean isOverdue(LocalDateTime now) {
        return !isResolved() && now.isAfter(slaDeadline());
    }

    /** True if the ticket was resolved on time. Only meaningful once resolved. */
    public boolean metSla() {
        return resolvedAt != null && !resolvedAt.isAfter(slaDeadline());
    }

    /** Hours from creation to resolution, or -1 if not resolved. */
    public double resolutionHours() {
        if (resolvedAt == null) {
            return -1;
        }
        return Duration.between(createdAt, resolvedAt).toMinutes() / 60.0;
    }

    @Override
    public String toString() {
        return String.format("#%-4d %-9s %-11s %-8s %-30s %s",
                id, priority, status, category, shorten(title, 30),
                assignee.isEmpty() ? "(unassigned)" : assignee);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
