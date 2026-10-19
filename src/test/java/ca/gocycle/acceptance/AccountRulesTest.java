package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.domain.*;
import ca.gocycle.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static ca.gocycle.acceptance.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountRulesTest {
    @Test void secondActiveTripIsRejectedBeforeHoldingOrUnlocking() throws Exception {
        var network = new Network();
        var first = bike();
        var second = bike();
        var origin = station("origin", 51, -114, true, List.of(dock("a", first), dock("b", second)));
        var stations = new StationService(Map.of("origin", origin, "destination", network.destination),
                Map.of(first.getId(), first, second.getId(), second), network.config, network.clock, network.lock);
        var trips = new TripService(network.riders, stations, network.pricing, network.payments,
                network.docking, network.config, network.clock, network.lock);
        var rider = network.registered();
        var trip = trips.startTrip(rider.getId(), first.getId(), PaymentMethod.CREDIT_CARD);
        assertThrows(IllegalStateException.class, () -> trips.startTrip(
                rider.getId(), second.getId(), PaymentMethod.CREDIT_CARD));
        assertEquals(1, network.gateway.holds);
        assertEquals(1, network.docking.unlocks);
        assertEquals(VehicleAvailability.AVAILABLE, second.getAvailability());
        trips.returnVehicle(trip.getId(), "destination");
        assertEquals(TripStatus.ACTIVE, trips.startTrip(
                rider.getId(), second.getId(), PaymentMethod.CREDIT_CARD).getStatus());
    }

    @Test void duplicateEmailCannotReplaceAnExistingRider() throws Exception {
        var network = new Network();
        var original = network.registered();
        assertThrows(IllegalArgumentException.class, () -> network.riders.register(
                "Other", "RIDER@UCALGARY.CA", LocalDate.of(2000, 1, 1)));
        assertSame(original, network.riders.findRider(original.getId()).orElseThrow());
    }

    @Test void consoleRequiresLoginAndStartsTripsForThatRider() throws Exception {
        var network = new Network();
        var app = app(network);
        var rejected = run(app, "start\nquit\n");
        assertTrue(rejected.contains("Log in before starting a trip"), rejected);
        assertEquals(0, network.gateway.holds);
        var output = run(app, registration("Test Rider", "rider@ucalgary.ca", "secret")
                + "login\nRIDER@UCALGARY.CA\nsecret\nstart\n" + network.vehicle.getId() + " CREDIT_CARD\nquit\n");
        assertTrue(output.contains("Welcome, Test Rider"), output);
        assertTrue(output.contains("Trip ID:"), output);
        assertEquals(1, network.gateway.holds);
        assertEquals(VehicleAvailability.RENTED, network.vehicle.getAvailability());
    }

    @Test void duplicateConsoleRegistrationKeepsTheOriginalPassword() throws Exception {
        var network = new Network();
        var output = run(app(network), registration("Original", "rider@ucalgary.ca", "secret")
                + registration("Replacement", "RIDER@UCALGARY.CA", "changed")
                + "login\nrider@ucalgary.ca\nsecret\nlogin\nrider@ucalgary.ca\nchanged\nquit\n");
        assertTrue(output.contains("An account already uses this login"), output);
        assertTrue(output.contains("Welcome, Original"), output);
        assertTrue(output.contains("Invalid login details"), output);
        assertFalse(output.contains("Welcome, Replacement"), output);
    }

    private ConsoleApplication app(Network network) {
        var dashboard = new OperatorDashboard(network.stations);
        var faults = new FaultService(List.of(dashboard), network.stations, network.config, network.clock);
        return new ConsoleApplication(network.config, network.riders, network.trips, faults, network.stations, dashboard);
    }

    private String registration(String name, String email, String password) {
        return "register\n" + name + "\n2000-01-01\n" + email + "\n" + password + "\nCASUAL\nCREDIT_CARD\n";
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
