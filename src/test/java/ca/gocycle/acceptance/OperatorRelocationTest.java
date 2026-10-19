package ca.gocycle.acceptance;

import ca.gocycle.application.FaultService;
import ca.gocycle.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static ca.gocycle.acceptance.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class OperatorRelocationTest {
    @Test void consoleRelocatesAVehicleAndShowsUpdatedOccupancyAndLocation() throws Exception {
        var network = new Network();
        var dashboard = new OperatorDashboard(network.stations);
        var app = app(network, dashboard);
        var before = dashboard.showNetworkState();
        assertTrue(before.contains("(origin) open occupied=1/1 free=0"), before);
        assertTrue(before.contains("location=origin/origin-dock"), before);
        var output = run(app, "help\nrelocate\n" + network.vehicle.getId() + " destination\nnetwork\nquit\n");
        assertTrue(output.contains("Vehicle relocated to destination"), output);
        assertTrue(output.contains("(origin) open occupied=0/1 free=1"), output);
        assertTrue(output.contains("(destination) open occupied=1/1 free=0"), output);
        assertTrue(output.contains("location=destination/destination-dock"), output);
        assertSame(network.vehicle, network.destination.getDockingPoints().getFirst().getVehicle());
        assertNull(network.origin.getDockingPoints().getFirst().getVehicle());
    }

    @Test void consoleRejectsAFullDestinationWithoutMovingTheVehicle() throws Exception {
        var network = new Network();
        network.destination.getDockingPoints().getFirst().attach(bike(), true);
        var dashboard = new OperatorDashboard(network.stations);
        var output = run(app(network, dashboard), "relocate\n" + network.vehicle.getId() + " destination\nquit\n");
        assertTrue(output.contains("Destination has no available dock"), output);
        assertSame(network.vehicle, network.origin.getDockingPoints().getFirst().getVehicle());
    }

    @Test void reservedVehiclesStayAtTheirSource() throws Exception {
        var network = new Network();
        network.vehicle.reserve(NOW);
        assertThrows(IllegalStateException.class,
                () -> network.stations.relocate(network.vehicle.getId(), "destination"));
        assertSame(network.vehicle, network.origin.getDockingPoints().getFirst().getVehicle());
        assertNull(network.destination.getDockingPoints().getFirst().getVehicle());
    }

    @Test void dashboardShowsAnActiveVehicleAsOnTrip() throws Exception {
        var network = new Network();
        network.start(network.registered());
        var text = new OperatorDashboard(network.stations).showNetworkState();
        assertTrue(text.contains("RENTED IN_SERVICE location=on trip"), text);
        assertTrue(text.contains("(origin) open occupied=0/1 free=1"), text);
    }

    private ConsoleApplication app(Network network, OperatorDashboard dashboard) {
        var faults = new FaultService(List.of(dashboard), network.stations, network.config, network.clock);
        return new ConsoleApplication(network.config, network.riders, network.trips, faults, network.stations, dashboard);
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
