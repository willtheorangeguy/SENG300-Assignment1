# Fleet, accounts and stations implementation

## Issue coverage

| Issues | Implemented responsibilities | Tests |
| --- | --- | --- |
| #2, #3 | Vehicle eligibility, reservation, failed-start release, rental, relocation, docking; pedal bikes have no battery dependency | `VehicleTest` |
| #4, #5, #6 | Shared electric battery checks, bikes and scooters, 20% threshold, configurable freshness, missing/future/stale readings, validated chronological updates | `VehicleTest`, enabled battery scenarios in `BatteryAndPricingTest` |
| #7, #8 | Paid membership interval, student evidence expiry, student/member exclusion, preference replacement and malformed-input rejection | `RiderMembershipTest` |
| #9, #10 | Exclusive station/dock composition, compatible free-dock lookup, physical occupancy, attach/detach and matching lock confirmation | `StationDockingPointTest` |

The existing public constructors and methods and the vehicle inheritance hierarchy remain intact. An additive `reserve(Instant at)` overload lets application services use their injected clock without a wall-clock mismatch; both Vehicle entries in `gocycle-class-diagram.xml` include it. Station composition is enforced with package-private ownership bookkeeping. Collection containers are immutable snapshots, while contained docking points remain live domain entities.

## Contracts and integration

- Malformed arguments throw `IllegalArgumentException`. Conflicting state transitions throw `IllegalStateException`. Failed operations validate before mutation.
- `Vehicle` keeps service status separate from availability. Faults and low battery never change `RENTED` to `AVAILABLE`. `markRented` requires a reservation; `markRelocating` requires availability; `markDocked` rejects reservations and otherwise records a confirmed return. Repeated docking confirmation is harmless.
- `isRentable(at)` and `reserve(at)` evaluate the supplied time. Trip services should call `reserve(clock.instant())` under the shared network lock so reservation and eligibility use the same clock. The original `reserve()` remains available and delegates using the current system instant.
- The electric threshold of 20% comes from the assignment. Battery maximum age comes from the constructor, must be positive, and is inclusive at its boundary. Future observations are not eligible at an earlier evaluation time. Updates reject missing timestamps, percentages outside 0..100, and observations older than the recorded reading; equal-time corrections are allowed. Telemetry never repairs a fault implicitly.
- Membership validity is `[validFrom, validUntil)`, and an unpaid membership (null payment reference) is inactive. The service supplies dates and the payment reference. It must charge the configured $15 and calculate 30 days from registration; these amounts are not duplicated in the domain object.
- Student evidence expires exclusively. Paid membership intervals cannot overlap the supplied student evidence. Expired evidence may coexist with a later membership. Account age, approved student institutions/email domains, and configured supported languages belong to `RiderService`; the domain validates required identity values and language-tag syntax.
- Dock attachment and lock confirmation record physical facts only. They do not stop trip charging, mark a vehicle available, or perform hardware I/O. The coordinating service must confirm unlock before detaching and confirm lock before ending a trip and calling `markDocked`.
- Free-dock lookup excludes closed stations, unusable docks, incompatible types and occupied docks. It is not a reservation; `attach` rechecks capacity atomically. Occupancy includes broken vehicles and unusable spaces in physical capacity. A zero-capacity station reports `0.0`.
- Dock IDs must be unique within a station. A docking-point object can belong to only one station. Ownership validation completes before any dock is claimed, so a failed station constructor does not consume otherwise unowned docks.
- Synchronized vehicle and dock mutations protect individual entities. Cross-entity trip and relocation transactions still need the application services' shared network lock.

## Verification

With JDK 21, run from the repository root:

```powershell
./mvnw.cmd clean verify
./mvnw.cmd '-Dtest=VehicleTest,RiderMembershipTest,StationDockingPointTest,BatteryAndPricingTest' test
```

Use `./mvnw` on macOS/Linux. The normal verification run passed 107 tests, with zero failures/errors. This includes 32 new domain test cases and five enabled battery acceptance cases. No assigned domain method remains a TODO.

The complete `./mvnw.cmd -Pacceptance test` run executed 162 cases before the two clock-injection cases were added: 105 passed, 20 failed and 37 errored. The 57 failing/error cases reach unfinished application/configuration/pricing/trip behavior (`RiderService`, `PaymentService`, `StationService`, `FaultService`, `PricingService`, `PricingConfiguration`, `ConfigurationLoader`, `ConfigurationValidator`, and `Trip`). These integration cases remain tagged pending; they were not disabled or rewritten to conceal failures.

The configured GitHub workflow runs the normal test suite. Local verification does not establish that a remote workflow has run. These commits are local until the feature branch is pushed.

## Team submission follow-up

The assignment requires the team to integrate its feature branches, finish the report PDF and design rationale, demonstrate two useful design patterns, include build/run instructions and all member UCIDs, include the UCID/email list, and provide the repository URL and exported commit log in one ZIP. The repository currently includes the report as a Word document; PDF export and final group submission are team tasks. This domain implementation does not establish that the complete application or submission is finished.
