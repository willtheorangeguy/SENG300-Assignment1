# Assignment 1 requirements audit

Audit date: October 19, 2026. Based on the supplied Assignment1.pdf and the team's
integrated main branch at `72fdcdb`, followed by the fixes in this branch.

## Required deliverables

| Requirement | Evidence |
| --- | --- |
| Functional, quality and constraint classification | Assignment 1 Report.pdf, pages 2-3 |
| Use case diagrams and main scenario descriptions | Report pages 6-10: start, return, fault reporting |
| At least eight classes; inheritance, composition and associations | Report page 11 and appendix; gocycle-class-diagram.xml; Vehicle hierarchy, Station/DockingPoint composition, Rider/Trip associations |
| SOLID rationale | Report pages 12-14; separate application services, injected integration interfaces |
| Two useful design patterns with justification, UML and trade-offs | Strategy for pricing and Observer for fault notifications, report pages 12-13 |
| Java core | src/main/java; runnable shaded JAR built with Java 21 |
| Shared repository and visible member contributions | README repository URL; commit_log.txt lists all five members' contributions and merges |
| Build/run instructions and member UCIDs | README.md |
| Member UCIDs and email addresses | EMAILS.md |
| Report PDF and one group ZIP | Report included in the prepared submission ZIP |

## Core behavior and verification

| Client behavior | Implementation / tests |
| --- | --- |
| Three vehicle types and 20% electric cutoff | Vehicle hierarchy; VehicleTest, BatteryAndPricingTest, BatteryReportingTest |
| Casual, member and student pricing; extendable categories | PricingPolicy strategies; PricingPolicyTest, TripPricingTest, FinalAuditTest |
| Unlock fee, rounded minutes, surcharge only beyond two hours | PricingService; nanosecond boundary regression cases in FinalAuditTest |
| Deposit before unlock; compensate failed starts | TripService / PaymentService; TripLifecycleTest and cancelled-start recovery regression |
| Confirm locking before stopping charges; nearest free station | TripService / StationService; TripLifecycleTest, StationsAndFaultsTest |
| Zone-specific payment methods and immutable trip prices | TripPricingTest, TripLifecycleTest, AccountsAndPaymentsTest |
| Student eligibility and paid membership | AccountRulesTest; renewal and expired-registration-window tests |
| Rider faults and electric telemetry; notify maintenance and operators | FaultService / FaultObserver; FaultDashboardTest, FaultRepairTest, BatteryReportingTest |
| Show network, relocate vehicles, restore service | OperatorDashboard / StationService; OperatorRelocationTest, FaultRepairTest |
| Multiple languages, saved preferences and zone branding | ConsoleApplicationTest, PresentationAcceptanceTest; en/fr console and simulated audio |
| Receipt after ended and paid trip; retry without duplicate fare | PaymentRecordsTest, AccountsAndPaymentsTest, FinalAuditTest |

All 291 tests pass under `./mvnw.cmd clean verify`, with zero failures, errors or
skips. The 23 former pending acceptance cases now run in the normal suite and CI.
The packaged-JAR smoke test covers registration, login, rental, cross-zone return,
settlement, receipt output and network display.

## Corrections made during this audit

- Preserve fractional seconds when rounding billable minutes and testing the
  two-hour surcharge boundary.
- Reject rental from closed stations or unusable docks before payment/unlock.
- Retry definitively failed deposit releases with a fresh attempt key. Reuse the
  same key after an ambiguous provider exception to avoid duplicate charges.
- Retain cancelled starts and their hold references; `settle` can recover their
  deposits without charging a fare or issuing a trip receipt.
- Renew an expired paid membership for a new 30-day term. Reject an already
  expired initial registration term before payment.
- Accept validated additional pricing categories without changing PricingService.
- Serialize account/payment operations, remove obsolete scaffold documentation,
  format application services, and enable all completed acceptance scenarios.

## Scope of the Java core

Part B asks for the core of the design, and the assignment overview explicitly
permits implementing part of the design. The executable is a console simulation:
payment processing, docking hardware, app/email receipt transport, branding and
audio output are simulated. State is in memory; the console is a local demo, not
a production authentication or payment system.

The report's extra assumptions for two-minute removal detection, twelve-hour
lost/stolen automation and deposit capture are future design extensions. The
configuration records those choices but no background scheduler enforces them.
The `close` command requires a real simulated lock confirmation; it does not
force an undocked trip to end. Operators cannot bypass the client's 20% battery
cutoff. These limits must not be represented as completed automated features.

First membership activation retains the report's registration-based 30-day term.
Renewal of an existing expired membership starts a fresh 30-day term when paid.
Faults block new rentals immediately while preserving an already-active ride.

## Group handoff

Review and merge the audit branch before using the shared main branch for the
submission. The prepared ZIP contains the audited branch snapshot, including all
team work already on main. Confirm the final report and simulation scope with the
group, and have one designated member submit one ZIP to the A1 Dropbox. The
outside-source list belongs in the submission comments, as required by the brief;
the repository's existing CITATIONS.md is preserved. TA/team registration and the
actual D2L submission cannot be verified from repository files.
