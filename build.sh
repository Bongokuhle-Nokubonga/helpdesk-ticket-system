#!/usr/bin/env bash
# Compile, test and (optionally) run the Help Desk Ticket System on Linux/macOS.
# Usage:  ./build.sh        -> compile + run tests
#         ./build.sh run    -> compile + start the app
set -euo pipefail
rm -rf out
javac --release 17 -d out $(find src -name "*.java")
if [ "${1:-}" = "run" ]; then
  java -cp out helpdesk.HelpDeskApp
else
  java -cp out helpdesk.TicketServiceTest
fi
