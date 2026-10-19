# GoCycle — SENG 300 Assignment 1

Original Repository URL: [https://github.com/willtheorangeguy/SENG300-Assignment1](https://github.com/willtheorangeguy/SENG300-Assignment1)

```text
William Vandergraaf - 30222999
Kahan Raval - 30245804
Elyas Hossaini - 30206499
Javad Mammadaiyev - 30268088
Yogita Bamrah - 30265974
```

## Build

1. Install **Java 21 (JDK)**. Check your installation with `java -version`.
2. Open a terminal in the project folder containing `pom.xml`.
3. Run the command for your operating system:

**Windows (PowerShell):**

```powershell
./mvnw.cmd clean package
```

**macOS / Linux:**

```sh
./mvnw clean package
```

## Run

```sh
java -jar target/gocycle-1.0.0.jar
```

The console starts with five demo stations in two zones. Each station has a pedal
bike, an electric bike, an electric scooter, and two free docks. Payments and
docking hardware are simulated.

Use `network` to see vehicle IDs, then `register` and `login` to create and access
a rider account. Use `start` and follow its prompts to rent a vehicle. Use
`return` with the trip ID and a station such as `campus-1`, then `settle` with the
trip ID and `APP` or `EMAIL`. Type `help` to see the commands or `quit` to exit.

After login, use `membership` with a station ID and payment method to activate or
renew a paid membership. Initial membership uses the report's 30-day registration
window; renewal after an existing membership expires starts a new 30-day period.
An expired initial registration window is rejected before charging.

## Verification

Run `./mvnw.cmd clean verify` on Windows or `./mvnw clean verify` on macOS/Linux.
All acceptance tests run in the normal build; none are tagged pending or excluded.
The `-Pacceptance` profile remains available for compatibility and runs the same suite.

Use `java -jar target/gocycle-1.0.0.jar --config config/gocycle.example.json` to
select a configuration explicitly. Run from the repository root so configuration
and branding assets resolve correctly. Java 21 is required; Maven is supplied by
the wrapper and downloads dependencies on the first build.

If a deposit release fails, retry `settle` with the trip ID. A failed unlock also
reports its cancelled trip ID so `settle` can retry the refund without charging a
fare. Payment retries reuse a request key when the provider outcome is unknown.

## Implementation scope

This is the Java core and console simulation requested in Part B. Payment,
docking hardware, app/email receipt delivery, branding, and audio are simulated;
`[audio]` is a visible accessibility cue, not speech synthesis. Accounts and state
are held in memory for one run. Do not use real passwords or payment credentials.

The report's extra assumptions about removal timers, lost/stolen-vehicle
automation and charging the deposit after 12 hours are proposed extensions, not
implemented background jobs. Battery overrides do not bypass the client's 20%
safety rule. The console's `close` command still requires a confirmed docked return.
See `REQUIREMENTS_AUDIT.md` for requirement coverage, verification, and submission checks.
