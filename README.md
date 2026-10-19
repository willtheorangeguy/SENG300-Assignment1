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

The package goal creates an executable jar with runtime dependencies included:

```powershell
java -jar target/gocycle-1.0.0.jar --config config/gocycle.example.json
```

On macOS/Linux, use the same `java -jar` command. Omit `--config PATH` to use the example deployment, or set `GOCYCLE_CONFIG` to select another configuration.
