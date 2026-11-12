# Compile, test and (optionally) run the Help Desk Ticket System on Windows.
# Usage:  .\build.ps1          -> compile + run tests
#         .\build.ps1 run      -> compile + start the app
$ErrorActionPreference = "Stop"
if (Test-Path out) { Remove-Item -Recurse -Force out }
$sources = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
javac --release 17 -d out $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($args[0] -eq "run") {
    java -cp out helpdesk.HelpDeskApp
} else {
    java -cp out helpdesk.TicketServiceTest
    exit $LASTEXITCODE
}
