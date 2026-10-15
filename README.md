# GoCycle — SENG 300 Assignment 1

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

## Verify the implemented domain behavior

```powershell
./mvnw.cmd clean verify
```

Use `./mvnw clean verify` on macOS/Linux. Fleet, rider/membership and station/dock implementation details, issue coverage, and integration dependencies are documented in [the domain implementation notes](docs/implementation/elyas-domain.md).

## Run the console

Copy runtime dependencies after building:

```powershell
./mvnw.cmd dependency:copy-dependencies -DincludeScope=runtime
java -cp "target/classes;target/dependency/*" ca.gocycle.presentation.ConsoleApplication --config config/gocycle.example.json
```

On macOS/Linux, use `./mvnw` and replace the classpath separator `;` with `:`.

**Current integration status:** console startup still depends on unfinished configuration/application services. The commands above identify the entry point but the full app is not yet runnable end to end. `./mvnw.cmd -Pacceptance test` includes the pending integration scenarios and currently fails on those stubs; the default verification suite runs completed scenarios.
