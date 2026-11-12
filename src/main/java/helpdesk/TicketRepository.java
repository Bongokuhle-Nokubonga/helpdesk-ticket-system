package helpdesk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Saves tickets to a tab-separated text file so they survive restarts.
 * Tabs, newlines, backslashes and pipes inside text are escaped.
 */
public class TicketRepository {
    private static final String HEADER =
            "id\ttitle\tdescription\trequester\tcategory\tpriority\tstatus\tassignee\tcreatedAt\tresolvedAt\tnotes";

    private final Path file;

    public TicketRepository(Path file) {
        this.file = file;
    }

    public List<Ticket> load() throws IOException {
        List<Ticket> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) { // skip header
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] f = line.split("\t", -1);
            if (f.length != 11) {
                throw new IOException("Bad line " + (i + 1) + " in " + file);
            }
            List<String> notes = new ArrayList<>();
            if (!f[10].isEmpty()) {
                for (String n : f[10].split("\\|", -1)) notes.add(unescape(n));
            }
            result.add(new Ticket(
                    Integer.parseInt(f[0]),
                    unescape(f[1]),
                    unescape(f[2]),
                    unescape(f[3]),
                    Category.valueOf(f[4]),
                    Priority.valueOf(f[5]),
                    LocalDateTime.parse(f[8]),
                    Status.valueOf(f[6]),
                    unescape(f[7]),
                    f[9].isEmpty() ? null : LocalDateTime.parse(f[9]),
                    notes));
        }
        return result;
    }

    public void save(Collection<Ticket> tickets) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Ticket t : tickets) {
            List<String> escapedNotes = new ArrayList<>();
            for (String n : t.getNotes()) escapedNotes.add(escape(n));
            lines.add(String.join("\t", Arrays.asList(
                    String.valueOf(t.getId()),
                    escape(t.getTitle()),
                    escape(t.getDescription()),
                    escape(t.getRequester()),
                    t.getCategory().name(),
                    t.getPriority().name(),
                    t.getStatus().name(),
                    escape(t.getAssignee()),
                    t.getCreatedAt().toString(),
                    t.getResolvedAt() == null ? "" : t.getResolvedAt().toString(),
                    String.join("|", escapedNotes))));
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '|' -> sb.append("\\p");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 'p' -> sb.append('|');
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
