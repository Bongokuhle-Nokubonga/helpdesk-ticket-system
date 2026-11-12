# Help Desk Ticket System (Java)

A command-line IT support ticketing system written in plain Java (no frameworks).
It models how a real help desk works: tickets are logged, prioritised, assigned
to technicians, tracked against **SLA (Service Level Agreement)** deadlines, and
reported on.

I built it to mirror the day-to-day work I did as an IT Technician Intern
supporting 30+ computer labs and 2,000+ workstations.

## Features

- Log tickets with a category (Hardware, Network, Software, Account, Printer) and priority
- SLA deadline per priority: **Critical 2h · High 8h · Medium 24h · Low 72h**
- Assign technicians; ticket moves from `OPEN` to `IN_PROGRESS` automatically
- Status rules: only `RESOLVED` tickets can be `CLOSED`; closed tickets are final; resolved tickets can be re-opened
- Work queue sorted by priority, then age
- Overdue list for tickets that breached their SLA
- Keyword search across title, description and requester
- Report: tickets by status and category, average resolution time, SLA compliance %
- Timestamped notes / audit trail on every ticket
- Saves to a text file (`tickets.tsv`) so data survives restarts
- 11 automated tests, run on every push with GitHub Actions

## Run it

Requires Java 17 or newer.

**Windows (PowerShell)**

```powershell
.\build.ps1        # compile and run tests
.\build.ps1 run    # start the app
```

**Linux / macOS**

```bash
./build.sh         # compile and run tests
./build.sh run     # start the app
```

Choose option `11` in the menu to load demo tickets.

## Example

```
ID    PRIORITY  STATUS      CATEGORY TITLE                          ASSIGNEE
#1    CRITICAL  IN_PROGRESS NETWORK  Lab 12 PCs have no network     Nokubonga
#2    MEDIUM    OPEN        PRINTER  Printer jams on every page     (unassigned)
#3    HIGH      RESOLVED    ACCOUNT  Password reset                 Nokubonga
#4    LOW       OPEN        HARDWARE Projector flickering           (unassigned)
```

## Project structure

```
src/main/java/helpdesk/
  HelpDeskApp.java       command-line menu
  TicketService.java     business rules (assign, status changes, SLA, reports)
  TicketRepository.java  save/load tickets to a file
  Ticket.java            the ticket model
  Priority.java          priorities and SLA hours
  Status.java, Category.java, Report.java
src/test/java/helpdesk/
  TicketServiceTest.java automated tests
```

## What I practised

- Object-oriented design: separating the model, business logic, storage and UI
- Enums, records, streams and switch expressions (modern Java)
- Testable code: the service takes a `Clock`, so tests can "move time forward" to check SLA breaches
- Safe file storage with escaping of special characters
- Continuous integration with GitHub Actions
