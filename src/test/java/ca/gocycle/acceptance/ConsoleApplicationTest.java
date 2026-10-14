package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.domain.station.Station;
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
import java.util.List;
import java.util.Map;
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
}
