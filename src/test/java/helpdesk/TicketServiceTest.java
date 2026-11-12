package helpdesk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Small self-contained test suite (no external libraries needed).
 * Run with: java -cp out helpdesk.TicketServiceTest
 */
public class TicketServiceTest {
    private static int passed = 0;
    private static int failed = 0;

    /** A clock the tests can move forward in time. */
    static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-05T08:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    public static void main(String[] args) throws Exception {
        test("create assigns increasing ids", () -> {
            TicketService s = new TicketService(new TestClock());
            Ticket a = s.create("A", "", "user", Category.NETWORK, Priority.LOW);
            Ticket b = s.create("B", "", "user", Category.NETWORK, Priority.LOW);
            check(a.getId() == 1 && b.getId() == 2, "ids should be 1 and 2");
        });

        test("blank title is rejected", () -> {
            TicketService s = new TicketService(new TestClock());
            expectThrows(() -> s.create("  ", "", "user", Category.NETWORK, Priority.LOW));
        });

        test("assigning moves OPEN to IN_PROGRESS", () -> {
            TicketService s = new TicketService(new TestClock());
            Ticket t = s.create("A", "", "user", Category.HARDWARE, Priority.LOW);
            s.assign(t.getId(), "Tech");
            check(t.getStatus() == Status.IN_PROGRESS, "status should be IN_PROGRESS");
            check(t.getAssignee().equals("Tech"), "assignee should be Tech");
        });

        test("cannot close a ticket that is not resolved", () -> {
            TicketService s = new TicketService(new TestClock());
            Ticket t = s.create("A", "", "user", Category.HARDWARE, Priority.LOW);
            expectThrows(() -> s.changeStatus(t.getId(), Status.CLOSED));
        });

        test("closed tickets cannot be changed", () -> {
            TicketService s = new TicketService(new TestClock());
            Ticket t = s.create("A", "", "user", Category.HARDWARE, Priority.LOW);
            s.changeStatus(t.getId(), Status.RESOLVED);
            s.changeStatus(t.getId(), Status.CLOSED);
            expectThrows(() -> s.assign(t.getId(), "Tech"));
        });

        test("re-opening clears the resolved time", () -> {
            TicketService s = new TicketService(new TestClock());
            Ticket t = s.create("A", "", "user", Category.HARDWARE, Priority.LOW);
            s.changeStatus(t.getId(), Status.RESOLVED);
            s.changeStatus(t.getId(), Status.OPEN);
            check(t.getResolvedAt() == null, "resolvedAt should be cleared");
        });

        test("critical ticket is overdue after 2 hours", () -> {
            TestClock clock = new TestClock();
            TicketService s = new TicketService(clock);
            s.create("Down", "", "user", Category.NETWORK, Priority.CRITICAL);
            s.create("Slow", "", "user", Category.NETWORK, Priority.LOW);
            clock.advance(Duration.ofHours(1));
            check(s.overdue().isEmpty(), "nothing overdue after 1h");
            clock.advance(Duration.ofHours(2));
            List<Ticket> overdue = s.overdue();
            check(overdue.size() == 1 && overdue.get(0).getTitle().equals("Down"),
                    "only the critical ticket is overdue");
        });

        test("queue sorts by priority then age", () -> {
            TicketService s = new TicketService(new TestClock());
            s.create("low", "", "u", Category.SOFTWARE, Priority.LOW);
            s.create("crit", "", "u", Category.SOFTWARE, Priority.CRITICAL);
            s.create("high", "", "u", Category.SOFTWARE, Priority.HIGH);
            List<Ticket> q = s.queue();
            check(q.get(0).getTitle().equals("crit") && q.get(2).getTitle().equals("low"),
                    "queue order should be crit, high, low");
        });

        test("report calculates SLA compliance", () -> {
            TestClock clock = new TestClock();
            TicketService s = new TicketService(clock);
            Ticket fast = s.create("fast", "", "u", Category.ACCOUNT, Priority.HIGH);  // 8h SLA
            Ticket slow = s.create("slow", "", "u", Category.ACCOUNT, Priority.HIGH);
            clock.advance(Duration.ofHours(2));
            s.changeStatus(fast.getId(), Status.RESOLVED);
            clock.advance(Duration.ofHours(10));
            s.changeStatus(slow.getId(), Status.RESOLVED);
            Report r = s.report();
            check(Math.abs(r.slaCompliancePercent() - 50.0) < 0.01, "compliance should be 50%");
            check(Math.abs(r.averageResolutionHours() - 7.0) < 0.01, "average should be 7h");
        });

        test("search is case-insensitive", () -> {
            TicketService s = new TicketService(new TestClock());
            s.create("Printer jam", "", "Admin", Category.PRINTER, Priority.LOW);
            s.create("VPN down", "", "Finance", Category.NETWORK, Priority.HIGH);
            check(s.search("PRINTER").size() == 1, "should find printer ticket");
            check(s.search("finance").size() == 1, "should search requester");
        });

        test("repository round-trips tickets with special characters", () -> {
            Path tmp = Files.createTempFile("tickets", ".tsv");
            try {
                TestClock clock = new TestClock();
                TicketService s = new TicketService(clock);
                Ticket t = s.create("Tab\there | pipe", "Line1\nLine2 \\ slash", "User",
                        Category.SOFTWARE, Priority.MEDIUM);
                s.assign(t.getId(), "Tech");
                s.addNote(t.getId(), "note with | pipe");
                s.changeStatus(t.getId(), Status.RESOLVED);

                TicketRepository repo = new TicketRepository(tmp);
                repo.save(s.all());
                List<Ticket> loaded = repo.load();
                Ticket l = loaded.get(0);
                check(l.getTitle().equals(t.getTitle()), "title survives");
                check(l.getDescription().equals(t.getDescription()), "description survives");
                check(l.getNotes().equals(t.getNotes()), "notes survive");
                check(l.getStatus() == Status.RESOLVED, "status survives");
                check(l.getResolvedAt().equals(t.getResolvedAt()), "resolvedAt survives");

                TicketService reloaded = new TicketService(clock, loaded);
                Ticket next = reloaded.create("Next", "", "u", Category.SOFTWARE, Priority.LOW);
                check(next.getId() == 2, "ids continue after reload");
            } finally {
                Files.deleteIfExists(tmp);
            }
        });

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    interface TestBody { void run() throws Exception; }

    private static void test(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL  " + name + " -> " + e.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectThrows(TestBody body) {
        try {
            body.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("expected an exception");
    }
}
