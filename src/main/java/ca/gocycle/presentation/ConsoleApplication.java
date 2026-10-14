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
import java.nio.file.Files;


public class ConsoleApplication {

    private DeploymentConfiguration configuration;

    private RiderService riderService;

    private TripService tripService;

    private FaultService faultService;

    private final StationService stationService;

    private OperatorDashboard dashboard;

    private Rider activeRider;

    private boolean audioEnabled;

    /** Store the application dependencies. */
    public ConsoleApplication(DeploymentConfiguration configuration, RiderService riderService, TripService tripService, FaultService faultService, StationService stationService, OperatorDashboard dashboard) {
        this.configuration = configuration;
        this.riderService = riderService;
        this.tripService = tripService;
        this.faultService = faultService;
        this.stationService = stationService;
        this.dashboard = dashboard;
    }

    /**
     * Load the selected configuration and start the application.
     * @param args required input; validate malformed values before mutation
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
        var configuration = new ConfigurationLoader(new ConfigurationValidator()).load(path);
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
    }

    /**
     * Provide registration/start/return/fault/operator flows in at least en/fr, zone branding, saved preferences and visible simulated audio.
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public void run() {
        var language = configuration.getInterfaces().defaultLanguage();
        if (!configuration.getInterfaces().supportedLanguages().contains(language)) {
            language = "en";
        }
        audioEnabled = false;
        var scanner = new Scanner(System.in);
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
                            ? "Choisissez: register, login, start, return, close, settle, fault, repair, battery, preferences, language, network, audio on/off, quit"
                            : "Choose: register, login, start, return, close, settle, fault, repair, battery, preferences, language, network, audio on/off, quit");
                    showBranding();
                    if (audioEnabled) System.out.println("[audio]");
                }
                case "login" -> language = loginRider(scanner, language);
                case "language" -> language = selectLanguage(scanner, language);
                case "audio on" -> {
                    audioEnabled = true;
                    savePreferences(language);
                    System.out.println(language.equals("fr") ? "Audio activé" : "Audio enabled");
                    System.out.println("[audio]");
                }
                case "audio off" -> {
                    audioEnabled = false;
                    savePreferences(language);
                    System.out.println(language.equals("fr") ? "Audio désactivé" : "Audio disabled");
                }
                case "network" -> System.out.println(dashboard.showNetworkState());
                case "register" -> registerRider(scanner, language);
                case "start" -> startTrip(scanner, language);
                case "return" -> returnTrip(scanner, language);
                case "settle" -> settleTrip(scanner, language);
                case "close" -> closeTrip(scanner, language);
                case "fault" -> reportFault(scanner, language);
                case "repair" -> changeServiceStatus(scanner, language);
                case "battery" -> updateBattery(scanner, language);
                case "preferences" -> language = updatePreferences(scanner, language);
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
            var tripId = UUID.fromString(fields[0]);
            var result = tripService.settleTrip(tripId, ReceiptChannel.valueOf(fields[1].toUpperCase(Locale.ROOT)));
            System.out.println(result.success() ? (language.equals("fr") ? "Paiement terminé" : "Payment complete")
                    : result.failureMessage());
            if (result.success()) {
                tripService.findTrip(tripId).ifPresent(trip -> showReceipt(trip, language));
            }
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void closeTrip(Scanner scanner, String language) {
        try {
            if (!configuration.getOperators().manualTripClosureAllowed()) {
                throw new IllegalStateException(language.equals("fr")
                        ? "La fermeture manuelle est désactivée" : "Manual trip closure is disabled");
            }
            System.out.print(language.equals("fr") ? "ID du trajet, station de retour, destination du reçu: "
                    : "Trip ID, return station, receipt destination: ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 3) throw new IllegalArgumentException("Enter all three values");
            var tripId = UUID.fromString(fields[0]);
            tripService.returnVehicle(tripId, fields[1]);
            var result = tripService.settleTrip(tripId, ReceiptChannel.valueOf(fields[2].toUpperCase(Locale.ROOT)));
            System.out.println(result.success()
                    ? (language.equals("fr") ? "Trajet fermé" : "Trip closed") : result.failureMessage());
            if (result.success()) tripService.findTrip(tripId).ifPresent(trip -> showReceipt(trip, language));
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
    }

    private void showReceipt(Trip trip, String language) {
        var fare = trip.getFare();
        var terms = trip.getPricingTerms();
        if (fare == null || terms == null) return;
        for (var field : configuration.getReceipts().fields()) {
            switch (field) {
                case "UNLOCK_FEE" -> printReceiptField(language.equals("fr") ? "Frais de déverrouillage" : "Unlock fee",
                        fare.unlockCharge());
                case "DURATION" -> printReceiptField(language.equals("fr") ? "Durée" : "Duration",
                        fare.billableMinutes() + (language.equals("fr") ? " minutes" : " minutes"));
                case "RATE" -> printReceiptField(language.equals("fr") ? "Tarif" : "Rate",
                        terms.perMinute() + " / minute");
                case "SURCHARGE" -> printReceiptField(language.equals("fr") ? "Supplément" : "Surcharge",
                        fare.surcharge());
                case "DEPOSIT_RELEASE" -> printReceiptField(language.equals("fr") ? "Dépôt" : "Deposit",
                        terms.deposit() + " " + trip.getDepositStatus());
                default -> { }
            }
        }
    }

    private void printReceiptField(String label, Object value) {
        System.out.println(label + ": " + value);
    }

    private String loginRider(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du client: " : "Rider ID: ");
            var riderId = UUID.fromString(scanner.nextLine().trim());
            activeRider = riderService.findRider(riderId)
                    .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));
            var preferences = activeRider.getPreferences();
            audioEnabled = preferences.audioEnabled();
            String preferredLanguage = configuration.getInterfaces().supportedLanguages().contains(preferences.language())
                    ? preferences.language() : language;
            System.out.println((preferredLanguage.equals("fr") ? "Bienvenue, " : "Welcome, ") + activeRider.getName());
            if (audioEnabled) System.out.println("[audio]");
            return preferredLanguage;
        } catch (RuntimeException exception) {
            showError(exception, language);
            return language;
        }
    }

    private String selectLanguage(Scanner scanner, String language) {
        System.out.print(language.equals("fr") ? "Langue: " : "Language: ");
        var selected = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
        if (!configuration.getInterfaces().supportedLanguages().contains(selected)) {
            System.out.println(language.equals("fr") ? "Langue non prise en charge" : "Unsupported language");
            return language;
        }
        savePreferences(selected);
        return selected;
    }

    private void savePreferences(String language) {
        if (activeRider != null) {
            riderService.updatePreferences(activeRider.getId(), new InterfacePreferences(language, audioEnabled));
        }
    }

    private void updateBattery(Scanner scanner, String language) {
        try {
            if (!configuration.getOperators().batteryOverrideAllowed()) {
                throw new IllegalStateException(language.equals("fr")
                        ? "La modification de batterie est désactivée" : "Battery override is disabled");
            }
            System.out.print(language.equals("fr") ? "ID du vélo et pourcentage de batterie: "
                    : "Vehicle ID and battery percentage: ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 2) throw new IllegalArgumentException("Enter both values");
            faultService.reportTelemetry(UUID.fromString(fields[0]), Integer.parseInt(fields[1]),
                    FaultType.LOW_BATTERY, faultService.getClock().instant());
            System.out.println(language.equals("fr") ? "Batterie mise à jour" : "Battery updated");
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
            if (!configuration.getOperators().faultOverrideAllowed()) {
                throw new IllegalStateException(language.equals("fr")
                        ? "La modification des pannes est désactivée" : "Fault override is disabled");
            }
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

    private String updatePreferences(Scanner scanner, String language) {
        try {
            System.out.print(language.equals("fr") ? "ID du client, langue (en/fr), audio (on/off): "
                    : "Rider ID, language (en/fr), audio (on/off): ");
            var fields = scanner.nextLine().trim().split("\\s+");
            if (fields.length != 3 || !(fields[2].equalsIgnoreCase("on") || fields[2].equalsIgnoreCase("off"))) {
                throw new IllegalArgumentException("Enter an ID, language, and on/off value");
            }
            riderService.updatePreferences(UUID.fromString(fields[0]),
                    new InterfacePreferences(fields[1].toLowerCase(Locale.ROOT), fields[2].equalsIgnoreCase("on")));
            if (activeRider != null && activeRider.getId().equals(UUID.fromString(fields[0]))) {
                audioEnabled = fields[2].equalsIgnoreCase("on");
                language = fields[1].toLowerCase(Locale.ROOT);
            }
            System.out.println(language.equals("fr") ? "Préférences enregistrées" : "Preferences saved");
        } catch (RuntimeException exception) {
            showError(exception, language);
        }
        return language;
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
