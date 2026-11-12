package helpdesk;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Command-line menu for the help desk ticket system.
 */
public class HelpDeskApp {
    private final TicketService service;
    private final TicketRepository repository;
    private final Scanner in = new Scanner(System.in);

    public HelpDeskApp(TicketService service, TicketRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "tickets.tsv");
        TicketRepository repo = new TicketRepository(dataFile);
        TicketService service = new TicketService(Clock.systemDefaultZone(), repo.load());
        System.out.println("Help Desk Ticket System  (data file: " + dataFile.toAbsolutePath() + ")");
        new HelpDeskApp(service, repo).run();
    }

    private void run() {
        while (true) {
            System.out.println("""

                    1) New ticket          6) Work queue (by priority)
                    2) List all tickets    7) Overdue tickets (SLA)
                    3) View ticket         8) Search
                    4) Assign technician   9) Report
                    5) Change status      10) Add note
                    11) Load demo data     0) Save and exit""");
            String choice = ask("Choose");
            try {
                switch (choice) {
                    case "1" -> newTicket();
                    case "2" -> print(service.all());
                    case "3" -> viewTicket();
                    case "4" -> service.assign(askInt("Ticket id"), ask("Technician name"));
                    case "5" -> service.changeStatus(askInt("Ticket id"),
                            askEnum("New status", Status.class));
                    case "6" -> print(service.queue());
                    case "7" -> print(service.overdue());
                    case "8" -> print(service.search(ask("Keyword")));
                    case "9" -> System.out.println(service.report().format());
                    case "10" -> service.addNote(askInt("Ticket id"), ask("Note"));
                    case "11" -> loadDemoData();
                    case "0" -> {
                        repository.save(service.all());
                        System.out.println("Saved. Goodbye!");
                        return;
                    }
                    default -> System.out.println("Unknown option.");
                }
                if (!choice.equals("0")) repository.save(service.all());
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Could not save: " + e.getMessage());
            }
        }
    }

    private void newTicket() {
        Ticket t = service.create(
                ask("Title"),
                ask("Description"),
                ask("Requester (name or department)"),
                askEnum("Category", Category.class),
                askEnum("Priority", Priority.class));
        System.out.println("Created ticket #" + t.getId() + ", due by " + t.slaDeadline());
    }

    private void viewTicket() {
        Ticket t = service.get(askInt("Ticket id"));
        System.out.println("Ticket #" + t.getId() + ": " + t.getTitle());
        System.out.println("  Requester:   " + t.getRequester());
        System.out.println("  Category:    " + t.getCategory());
        System.out.println("  Priority:    " + t.getPriority() + " (SLA " + t.getPriority().slaHours() + "h)");
        System.out.println("  Status:      " + t.getStatus());
        System.out.println("  Assignee:    " + (t.getAssignee().isEmpty() ? "(unassigned)" : t.getAssignee()));
        System.out.println("  Created:     " + t.getCreatedAt());
        System.out.println("  SLA due:     " + t.slaDeadline());
        if (t.getResolvedAt() != null) {
            System.out.printf("  Resolved:    %s (%.1fh, %s)%n", t.getResolvedAt(),
                    t.resolutionHours(), t.metSla() ? "within SLA" : "SLA missed");
        }
        System.out.println("  Description: " + t.getDescription());
        System.out.println("  Notes:");
        t.getNotes().forEach(n -> System.out.println("    " + n));
    }

    private void loadDemoData() {
        Ticket a = service.create("Lab 12 PCs have no network", "20 workstations show 'No internet'. Switch port lights off.",
                "Engineering Faculty", Category.NETWORK, Priority.CRITICAL);
        service.create("Printer jams on every page", "HP LaserJet in admin office, room 3-104.",
                "Admin Office", Category.PRINTER, Priority.MEDIUM);
        Ticket c = service.create("Password reset", "User locked out of Microsoft 365 after 5 attempts.",
                "J. Mokoena", Category.ACCOUNT, Priority.HIGH);
        service.create("Projector flickering", "Lecture hall B projector flickers after 10 minutes.",
                "Lecture Hall B", Category.HARDWARE, Priority.LOW);
        service.assign(a.getId(), "Nokubonga");
        service.addNote(a.getId(), "Found tripped breaker on access switch; power restored.");
        service.assign(c.getId(), "Nokubonga");
        service.changeStatus(c.getId(), Status.RESOLVED);
        System.out.println("Added 4 demo tickets.");
    }

    private void print(List<Ticket> list) {
        if (list.isEmpty()) {
            System.out.println("(no tickets)");
            return;
        }
        System.out.println(String.format("%-5s %-9s %-11s %-8s %-30s %s",
                "ID", "PRIORITY", "STATUS", "CATEGORY", "TITLE", "ASSIGNEE"));
        list.forEach(System.out::println);
    }

    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        if (!in.hasNextLine()) {
            System.exit(0);
        }
        return in.nextLine().replace("﻿", "").trim(); // ignore byte-order marks from piped input
    }

    private int askInt(String prompt) {
        String s = ask(prompt).replace("#", "");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a number");
        }
    }

    private <E extends Enum<E>> E askEnum(String prompt, Class<E> type) {
        StringBuilder options = new StringBuilder();
        for (E e : type.getEnumConstants()) {
            if (options.length() > 0) options.append('/');
            options.append(e.name());
        }
        String s = ask(prompt + " [" + options + "]").toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Choose one of: " + options);
        }
    }
}
