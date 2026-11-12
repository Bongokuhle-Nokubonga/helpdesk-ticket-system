package helpdesk;

import java.util.Map;

/**
 * Summary numbers a help desk team lead would look at.
 */
public record Report(
        int totalTickets,
        Map<Status, Long> byStatus,
        Map<Category, Long> byCategory,
        int overdueCount,
        double averageResolutionHours,
        double slaCompliancePercent) {

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Help Desk Report ===\n");
        sb.append("Total tickets: ").append(totalTickets).append('\n');
        sb.append("\nBy status:\n");
        byStatus.forEach((k, v) -> sb.append(String.format("  %-12s %d%n", k, v)));
        sb.append("\nBy category:\n");
        byCategory.forEach((k, v) -> sb.append(String.format("  %-12s %d%n", k, v)));
        sb.append(String.format("%nOverdue (SLA breached, still open): %d%n", overdueCount));
        if (averageResolutionHours < 0) {
            sb.append("Average resolution time: n/a (nothing resolved yet)\n");
            sb.append("SLA compliance: n/a\n");
        } else {
            sb.append(String.format("Average resolution time: %.1f hours%n", averageResolutionHours));
            sb.append(String.format("SLA compliance: %.0f%%%n", slaCompliancePercent));
        }
        return sb.toString();
    }
}
