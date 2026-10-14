package ca.gocycle.presentation;

import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ca.gocycle.application.*;
import ca.gocycle.config.*;
import ca.gocycle.data.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.fault.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.nio.file.Files;
import java.util.concurrent.locks.ReentrantLock;


public class ConsoleApplication {

    private DeploymentConfiguration configuration;

    private RiderService riderService;

    private TripService tripService;

    private FaultService faultService;

    private final StationService stationService;

    private OperatorDashboard dashboard;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public ConsoleApplication(DeploymentConfiguration configuration, RiderService riderService, TripService tripService, FaultService faultService, StationService stationService, OperatorDashboard dashboard) {
        this.configuration = configuration;
        this.riderService = riderService;
        this.tripService = tripService;
        this.faultService = faultService;
        this.stationService = stationService;
        this.dashboard = dashboard;
    }

    /**
     * Select --config PATH over GOCYCLE_CONFIG over the example, then validate before wiring shared services, Clock, lock, strategies, observers and simulators. Startup remains TODO.
     * @param args required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 0 && (args.length != 2 || !"--config".equals(args[0])))) {
            throw new IllegalArgumentException("Usage: ConsoleApplication [--config PATH]");
        }
        String selected = args.length == 2 ? args[1] : System.getenv("GOCYCLE_CONFIG");
        Path path = Path.of(selected == null || selected.isBlank() ? "config/gocycle.example.json" : selected);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Configuration file not found: " + path);
        }
        try {
            var mapper = new ObjectMapper().findAndRegisterModules();
            var configuration = mapper.readValue(path.toFile(), DeploymentConfiguration.class);
            var validationErrors = new ConfigurationValidator().validate(configuration);
            if (!validationErrors.isEmpty()) {
                throw new IllegalArgumentException(String.join("; ", validationErrors));
            }
            var clock = Clock.systemUTC();
            var lock = new ReentrantLock();
            var gateway = new SimulatedPaymentGateway(true, true, true);
            var delivery = new SimulatedReceiptDelivery(true);
            var payments = new PaymentService(gateway, delivery, configuration, clock);
            var riders = new RiderService(payments, configuration, clock);
            var stations = new StationService(Map.of(), Map.of(), configuration, clock, lock);
            var pricing = new PricingService(List.of(new StudentPricingPolicy(), new MemberPricingPolicy(),
                    new CasualPricingPolicy()), configuration);
            var trips = new TripService(riders, stations, pricing, payments,
                    new SimulatedDockingGateway(true, true), configuration, clock, lock);
            var dashboard = new OperatorDashboard(stations);
            var faults = new FaultService(List.of(dashboard, new MaintenanceNotifier()), stations, configuration, clock);
            new ConsoleApplication(configuration, riders, trips, faults, stations, dashboard).run();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read configuration: " + path, exception);
        }
    }

    /**
     * Provide registration/start/return/fault/operator flows in at least en/fr, zone branding, saved preferences and visible simulated audio.
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public void run() {
        var language = configuration.getInterfaces().defaultLanguage();
        if (!configuration.getInterfaces().supportedLanguages().contains(language)) {
            language = configuration.getInterfaces().defaultLanguage();
        }
        var scanner = new Scanner(System.in);
        boolean audioEnabled = false;
        showBranding();
        while (true) {
            System.out.print(language.equals("fr") ? "Commande (aide pour les options): " : "Command (help for options): ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String command = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            switch (command) {
                case "quit", "exit", "quitter" -> { return; }
                case "help", "aide" -> {
                    System.out.println(language.equals("fr")
                            ? "Choisissez: register, start, return, settle, fault, repair, preferences, network, audio on/off, quit"
                            : "Choose: register, start, return, settle, fault, repair, preferences, network, audio on/off, quit");
                    showBranding();
                    if (audioEnabled) System.out.println("[audio]");
                }
                case "audio on" -> {
                    audioEnabled = true;
                    System.out.println(language.equals("fr") ? "Audio activé" : "Audio enabled");
                    System.out.println("[audio]");
                }
                case "audio off" -> {
                    audioEnabled = false;
                    System.out.println(language.equals("fr") ? "Audio désactivé" : "Audio disabled");
                }
                case "network" -> System.out.println(dashboard.showNetworkState());
                case "register" -> registerRider(scanner, language);
                case "start" -> startTrip(scanner, language);
                case "return" -> returnTrip(scanner, language);
                case "settle" -> settleTrip(scanner, language);
                case "fault" -> reportFault(scanner, language);
                case "repair" -> changeServiceStatus(scanner, language);
                case "preferences" -> updatePreferences(scanner, language);
                default -> System.out.println(language.equals("fr") ? "Commande inconnue" : "Unknown command");
            }
        }
    }

    private void showBranding() {
        for (var station : stationService.listStations()) {
            var display = station.getZone().getDisplay();
            System.out.println(display.primaryColour() + " " + display.logoPath());
        }
    }

    private void registerRider(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "Nom: " : "Name: ");
            var name = scanner.nextLine().trim();
            System.out.print(language.equals("fr") ? "Courriel: " : "Email: ");
            var email = scanner.nextLine().trim();
            System.out.print(language.equals("fr") ? "Date de naissance (AAAA-MM-JJ): " : "Birth date (YYYY-MM-DD): ");
            var birthDate = LocalDate.parse(scanner.nextLine().trim());
            var rider = riderService.register(name, email, birthDate);
            System.out.println("Rider ID: " + rider.getId());
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void startTrip(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du client, ID du vélo, mode de paiement: "
                    : "Rider ID, vehicle ID, payment method: ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 3) throw new IllegalArgumentException("Enter all three values");
            var trip = tripService.startTrip(UUID.fromString(fields[0]), UUID.fromString(fields[1]),
                    PaymentMethod.valueOf(fields[2].toUpperCase(Locale.ROOT)));
            System.out.println("Trip ID: " + trip.getId());
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void returnTrip(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du trajet et ID de la station: " : "Trip ID and station ID: ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 2) throw new IllegalArgumentException("Enter both values");
            var suggested = tripService.returnVehicle(UUID.fromString(fields[0]), fields[1]);
            System.out.println(suggested.map(station -> "Suggested station: " + station.getId())
                    .orElse(language.equals("fr") ? "Vélo retourné" : "Vehicle returned"));
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void settleTrip(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du trajet et destination (APP/EMAIL): "
                    : "Trip ID and receipt destination (APP/EMAIL): ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 2) throw new IllegalArgumentException("Enter both values");
            var result = tripService.settleTrip(UUID.fromString(fields[0]),
                    ReceiptChannel.valueOf(fields[1].toUpperCase(Locale.ROOT)));
            System.out.println(result.success() ? (language.equals("fr") ? "Paiement terminé" : "Payment complete")
                    : result.failureMessage());
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void reportFault(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du vélo, type, description: " : "Vehicle ID, fault type, description: ");
            var fields = scanner.nextLine().trim().split("\\s+", 3);
            if (fields.length != 3) throw new IllegalArgumentException("Enter all three fields");
            var report = faultService.reportProblem(UUID.fromString(fields[0]),
                    FaultType.valueOf(fields[1].toUpperCase(Locale.ROOT)), fields[2]);
            System.out.println("Fault ID: " + report.getId());
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void changeServiceStatus(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du vélo, état, note de réparation: "
                    : "Vehicle ID, service status, repair note: ");
            var fields = scanner.nextLine().trim().split("\\s+", 3);
            if (fields.length != 3) throw new IllegalArgumentException("Enter all three fields");
            faultService.changeServiceStatus(UUID.fromString(fields[0]),
                    VehicleServiceStatus.valueOf(fields[1].toUpperCase(Locale.ROOT)), fields[2]);
            System.out.println(language.equals("fr") ? "État mis à jour" : "Service status updated");
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void updatePreferences(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du client, langue (en/fr), audio (on/off): "
                    : "Rider ID, language (en/fr), audio (on/off): ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 3 || !(fields[2].equalsIgnoreCase("on") || fields[2].equalsIgnoreCase("off"))) {
                throw new IllegalArgumentException("Enter an ID, language, and on/off value");
            }
            riderService.updatePreferences(UUID.fromString(fields[0]),
                    new InterfacePreferences(fields[1].toLowerCase(Locale.ROOT), fields[2].equalsIgnoreCase("on")));
            System.out.println(language.equals("fr") ? "Préférences enregistrées" : "Preferences saved");
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void showError(RuntimeException exception, String language) {
        String message = exception.getMessage();
        System.out.println((language.equals("fr") ? "Erreur: " : "Error: ")
                + (message == null ? exception.getClass().getSimpleName() : message));
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public DeploymentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public RiderService getRiderService() {
        return riderService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public TripService getTripService() {
        return tripService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public FaultService getFaultService() {
        return faultService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public StationService getStationService() {
        return stationService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public OperatorDashboard getDashboard() {
        return dashboard;
    }
}
