package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.data.FareBreakdown;
import ca.gocycle.data.InterfacePreferences;
import ca.gocycle.data.PaymentResult;
import ca.gocycle.domain.DepositStatus;
import ca.gocycle.domain.PaymentMethod;
import ca.gocycle.domain.PaymentStatus;
import ca.gocycle.domain.ReceiptChannel;
import ca.gocycle.domain.TripStatus;
import ca.gocycle.domain.rider.Rider;
import ca.gocycle.domain.station.Station;
import ca.gocycle.domain.trip.Trip;
import ca.gocycle.fault.MaintenanceNotifier;
import ca.gocycle.presentation.ConsoleApplication;
import ca.gocycle.presentation.OperatorDashboard;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleApplicationTest {
    @ParameterizedTest
    @CsvSource({"en,Choose", "fr,Choisissez"})
    void helpUsesConfiguredLanguageAndBranding(String language, String instruction) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.valueToTree(Fixtures.config());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("interfaces")).put("defaultLanguage", language);
        var configuration = mapper.treeToValue(tree, ca.gocycle.config.DeploymentConfiguration.class);
        var station = Fixtures.station("central", 51.0, -114.0, true, List.of());
        var clock = Clock.systemUTC();
        var lock = new ReentrantLock();
        var stations = new StationService(Map.of("central", station), Map.of(), configuration, clock, lock) {
            @Override public List<Station> listStations() { return List.of(station); }
            @Override public List<ca.gocycle.domain.vehicle.Vehicle> listVehicles() { return List.of(); }
        };
        var payments = new PaymentService(new SimulatedPaymentGateway(true, true, true),
                new SimulatedReceiptDelivery(true), configuration, clock);
        var riders = new RiderService(payments, configuration, clock);
        var pricing = new PricingService(List.of(new StudentPricingPolicy(), new MemberPricingPolicy(),
                new CasualPricingPolicy()), configuration);
        var trips = new TripService(riders, stations, pricing, payments,
                new SimulatedDockingGateway(true, true), configuration, clock, lock);
        var dashboard = new OperatorDashboard(stations);
        var faults = new FaultService(List.of(dashboard, new MaintenanceNotifier()), stations, configuration, clock);
        var app = new ConsoleApplication(configuration, riders, trips, faults, stations, dashboard);
        var oldInput = System.in;
        var oldOutput = System.out;
        var output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("help\naudio on\nhelp\nquit\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            app.run();
        } finally {
            System.setIn(oldInput);
            System.setOut(oldOutput);
        }
        var rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains(instruction));
        assertTrue(rendered.contains("#176B56"));
        assertTrue(rendered.contains("central-logo.svg"));
        assertTrue(rendered.contains("[audio]"));
    }

    @Test
    void explicitMissingConfigurationFailsBeforeStartup() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> ConsoleApplication.main(new String[]{"--config", "config/missing-explicit.json"}));
        assertTrue(error.getMessage().contains("missing-explicit.json"));
    }

    @Test
    void loginLoadsPreferencesAndSettlementPrintsReceiptFields() throws Exception {
        var configuration = Fixtures.config();
        var rider = new Rider(UUID.randomUUID(), "Test Rider", "rider@ucalgary.ca", LocalDate.of(2000, 1, 1),
                null, null, null, new InterfacePreferences("fr", true));
        var fare = new FareBreakdown(new BigDecimal("5.00"), 2, new BigDecimal("1.00"),
                BigDecimal.ZERO, new BigDecimal("6.00"));
        var trip = new Trip(UUID.randomUUID(), rider, Fixtures.bike(), "central", "dock-1",
                Instant.parse("2026-10-01T12:00:00Z"), Instant.parse("2026-10-01T12:02:00Z"),
                TripStatus.ENDED, PaymentStatus.PAID, DepositStatus.RELEASED, Fixtures.terms("CASUAL"),
                "hold-1", "charge-1", PaymentMethod.CREDIT_CARD, Set.of(PaymentMethod.CREDIT_CARD), fare);
        var station = Fixtures.station("central", 51.0, -114.0, true, List.of());
        var clock = Clock.systemUTC();
        var lock = new ReentrantLock();
        var stations = new StationService(Map.of("central", station), Map.of(), configuration, clock, lock) {
            @Override public List<Station> listStations() { return List.of(station); }
            @Override public List<ca.gocycle.domain.vehicle.Vehicle> listVehicles() { return List.of(); }
        };
        var payments = new PaymentService(new SimulatedPaymentGateway(true, true, true),
                new SimulatedReceiptDelivery(true), configuration, clock);
        var riders = new RiderService(payments, configuration, clock) {
            @Override public Rider register(String name, String email, LocalDate birthDate) {
                assertEquals("Test Rider", name);
                assertEquals("rider@ucalgary.ca", email);
                return rider;
            }
            @Override public Optional<Rider> findRider(UUID id) { return Optional.of(rider); }
        };
        var pricing = new PricingService(List.of(new StudentPricingPolicy(), new MemberPricingPolicy(),
                new CasualPricingPolicy()), configuration);
        var trips = new TripService(riders, stations, pricing, payments,
                new SimulatedDockingGateway(true, true), configuration, clock, lock) {
            @Override public PaymentResult settleTrip(UUID id, ReceiptChannel channel) {
                assertEquals(ReceiptChannel.APP, channel);
                return new PaymentResult(true, "charge-1", null, null);
            }
            @Override public Optional<Trip> findTrip(UUID id) { return Optional.of(trip); }
        };
        var dashboard = new OperatorDashboard(stations);
        var faults = new FaultService(List.of(dashboard), stations, configuration, clock);
        var app = new ConsoleApplication(configuration, riders, trips, faults, stations, dashboard);
        var output = run(app, "register\nTest Rider\n2000-01-01\nrider@ucalgary.ca\nsecret\nCASUAL\nCREDIT_CARD\n"
                + "login\nrider@ucalgary.ca\nsecret\nsettle\n" + trip.getId() + " APP\nquit\n");
        assertTrue(output.contains("Bienvenue, Test Rider"));
        assertTrue(output.contains("[audio]"));
        assertTrue(output.contains("Frais de déverrouillage: 5.00"), output);
        assertTrue(output.contains("Durée: 2 minutes"));
        assertTrue(output.contains("Tarif: 0.50 / minute"));
        assertTrue(output.contains("Supplément: 0"));
        assertTrue(output.contains("Dépôt: 50.00 RELEASED"));
    }

    @Test
    void manualClosureRespectsConfiguration() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.valueToTree(Fixtures.config());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("operators")).put("manualTripClosureAllowed", false);
        var configuration = mapper.treeToValue(tree, ca.gocycle.config.DeploymentConfiguration.class);
        var clock = Clock.systemUTC();
        var lock = new ReentrantLock();
        var stations = new StationService(Map.of(), Map.of(), configuration, clock, lock) {
            @Override public List<Station> listStations() { return List.of(); }
            @Override public List<ca.gocycle.domain.vehicle.Vehicle> listVehicles() { return List.of(); }
        };
        var payments = new PaymentService(new SimulatedPaymentGateway(true, true, true),
                new SimulatedReceiptDelivery(true), configuration, clock);
        var riders = new RiderService(payments, configuration, clock);
        var pricing = new PricingService(List.of(new StudentPricingPolicy(), new MemberPricingPolicy(),
                new CasualPricingPolicy()), configuration);
        var returned = new AtomicInteger();
        var trips = new TripService(riders, stations, pricing, payments,
                new SimulatedDockingGateway(true, true), configuration, clock, lock) {
            @Override public Optional<Station> returnVehicle(UUID id, String stationId) {
                returned.incrementAndGet();
                return Optional.empty();
            }
        };
        var dashboard = new OperatorDashboard(stations);
        var faults = new FaultService(List.of(dashboard), stations, configuration, clock);
        var app = new ConsoleApplication(configuration, riders, trips, faults, stations, dashboard);
        var output = run(app, "close\n" + UUID.randomUUID() + " central APP\nquit\n");
        assertTrue(output.contains("Manual trip closure is disabled"));
        assertEquals(0, returned.get());
    }

    private String run(ConsoleApplication app, String input) {
        var oldInput = System.in;
        var oldOutput = System.out;
        var output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            app.run();
        } finally {
            System.setIn(oldInput);
            System.setOut(oldOutput);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
