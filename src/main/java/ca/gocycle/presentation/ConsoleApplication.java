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
                            ? "Choisissez: help, audio on, audio off, network, quit"
                            : "Choose: help, audio on, audio off, network, quit");
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
