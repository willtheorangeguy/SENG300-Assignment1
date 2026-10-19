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

To use another configuration, add `--config path/to/config.json` to the run command.

