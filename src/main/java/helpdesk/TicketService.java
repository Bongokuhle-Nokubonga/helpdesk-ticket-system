package helpdesk;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business rules for creating, assigning and resolving tickets.
 */
public class TicketService {
    private static final DateTimeFormatter NOTE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Map<Integer, Ticket> tickets = new LinkedHashMap<>();
    private final Clock clock;
    private int nextId = 1;

    public TicketService(Clock clock) {
        this.clock = clock;
    }

    public TicketService(Clock clock, Collection<Ticket> existing) {
        this(clock);
        for (Ticket t : existing) {
            tickets.put(t.getId(), t);
            nextId = Math.max(nextId, t.getId() + 1);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Ticket create(String title, String description, String requester,
                         Category category, Priority priority) {
        requireText(title, "Title");
        requireText(requester, "Requester");
        if (category == null || priority == null) {
            throw new IllegalArgumentException("Category and priority are required");
        }
        Ticket t = new Ticket(nextId++, title.trim(),
                description == null ? "" : description.trim(),
                requester.trim(), category, priority, now());
        tickets.put(t.getId(), t);
        return t;
    }

    public Ticket get(int id) {
        Ticket t = tickets.get(id);
        if (t == null) {
            throw new IllegalArgumentException("No ticket with id " + id);
        }
        return t;
    }

    /** Assigns a technician. An OPEN ticket moves to IN_PROGRESS. */
    public void assign(int id, String technician) {
        requireText(technician, "Technician");
        Ticket t = get(id);
        requireNotClosed(t);
        t.setAssignee(technician.trim());
        if (t.getStatus() == Status.OPEN) {
            t.setStatus(Status.IN_PROGRESS);
        }
        t.addNote(stamp("Assigned to " + technician.trim()));
    }

    public void changeStatus(int id, Status newStatus) {
        Ticket t = get(id);
        requireNotClosed(t);
        if (t.getStatus() == newStatus) {
            return;
        }
        if (newStatus == Status.CLOSED && t.getStatus() != Status.RESOLVED) {
            throw new IllegalStateException("Only RESOLVED tickets can be closed");
        }
        if (newStatus == Status.RESOLVED) {
            t.setResolvedAt(now());
        } else if (newStatus == Status.OPEN || newStatus == Status.IN_PROGRESS) {
            t.setResolvedAt(null); // re-opened
        }
        t.addNote(stamp("Status " + t.getStatus() + " -> " + newStatus));
        t.setStatus(newStatus);
    }

    public void addNote(int id, String note) {
        requireText(note, "Note");
        Ticket t = get(id);
        t.addNote(stamp(note.trim()));
    }

    public List<Ticket> all() {
        return new ArrayList<>(tickets.values());
    }

    public List<Ticket> byStatus(Status status) {
        return tickets.values().stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }

    /** Case-insensitive search in title, description and requester. */
    public List<Ticket> search(String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT).trim();
        return tickets.values().stream()
                .filter(t -> t.getTitle().toLowerCase(Locale.ROOT).contains(k)
                        || t.getDescription().toLowerCase(Locale.ROOT).contains(k)
                        || t.getRequester().toLowerCase(Locale.ROOT).contains(k))
                .collect(Collectors.toList());
    }

    /** Unresolved tickets past their SLA deadline, most overdue first. */
    public List<Ticket> overdue() {
        LocalDateTime now = now();
        return tickets.values().stream()
                .filter(t -> t.isOverdue(now))
                .sorted(Comparator.comparing(Ticket::slaDeadline))
                .collect(Collectors.toList());
    }

    /** Open work queue: highest priority first, then oldest first. */
    public List<Ticket> queue() {
        return tickets.values().stream()
                .filter(t -> !t.isResolved())
                .sorted(Comparator.comparing(Ticket::getPriority).reversed()
                        .thenComparing(Ticket::getCreatedAt))
                .collect(Collectors.toList());
    }

    public Report report() {
        Map<Status, Long> byStatus = new EnumMap<>(Status.class);
        Map<Category, Long> byCategory = new EnumMap<>(Category.class);
        for (Status s : Status.values()) byStatus.put(s, 0L);
        for (Category c : Category.values()) byCategory.put(c, 0L);

        double totalHours = 0;
        int resolved = 0;
        int onTime = 0;
        for (Ticket t : tickets.values()) {
            byStatus.merge(t.getStatus(), 1L, Long::sum);
            byCategory.merge(t.getCategory(), 1L, Long::sum);
            if (t.getResolvedAt() != null) {
                resolved++;
                totalHours += t.resolutionHours();
                if (t.metSla()) onTime++;
            }
        }
        double avg = resolved == 0 ? -1 : totalHours / resolved;
        double compliance = resolved == 0 ? -1 : 100.0 * onTime / resolved;
        return new Report(tickets.size(), byStatus, byCategory, overdue().size(), avg, compliance);
    }

    private String stamp(String text) {
        return "[" + now().format(NOTE_TIME) + "] " + text;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }

    private static void requireNotClosed(Ticket t) {
        if (t.getStatus() == Status.CLOSED) {
            throw new IllegalStateException("Ticket #" + t.getId() + " is closed");
        }
    }
}
