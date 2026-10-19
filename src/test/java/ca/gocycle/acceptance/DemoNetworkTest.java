package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.domain.*;
import ca.gocycle.presentation.ConsoleApplication;
import ca.gocycle.simulation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.locks.ReentrantLock;
import static ca.gocycle.acceptance.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DemoNetworkTest {
    @Test void demoSupportsACompleteTripAcrossZones() throws Exception {
        var configuration = config();
        var clock = new MutableClock();
        var lock = new ReentrantLock();
        var stations = DemoNetwork.create(configuration, clock, lock);
        assertEquals(5, stations.listStations().size());
        assertEquals(15, stations.listVehicles().size());
        assertEquals(3, stations.listVehicles().stream().map(v -> v.getType()).distinct().count());
        for (var station : stations.listStations()) {
            assertEquals(5, station.getDockingPoints().size());
            assertEquals(2, station.getDockingPoints().stream().filter(d -> d.getVehicle() == null).count());
        }
        var delivery = new SimulatedReceiptDelivery(true);
        var payments = new PaymentService(new SimulatedPaymentGateway(true, true, true), delivery,
                configuration, clock);
        var riders = new RiderService(payments, configuration, clock);
        var trips = new TripService(riders, stations, pricing(configuration), payments,
                new SimulatedDockingGateway(true, true), configuration, clock, lock);
        var rider = riders.register("Demo Rider", "demo@example.com", LocalDate.of(2000, 1, 1));
        var vehicle = stations.findStation("central-1").orElseThrow().getDockingPoints().get(1).getVehicle();
        var trip = trips.startTrip(rider.getId(), vehicle.getId(), PaymentMethod.CREDIT_CARD);
        clock.advance(Duration.ofMinutes(2));
        assertTrue(trips.returnVehicle(trip.getId(), "campus-1").isEmpty());
        assertTrue(trips.settleTrip(trip.getId(), ReceiptChannel.APP).success());
        assertEquals(DepositStatus.RELEASED, trip.getDepositStatus());
        assertEquals(1, delivery.getDeliveredReceipts().size());
    }

    @Test void mainStartsWithStationsAndVehicles() {
        var oldInput = System.in;
        var oldOutput = System.out;
        var output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("network\nquit\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ConsoleApplication.main(new String[0]);
        } finally {
            System.setIn(oldInput);
            System.setOut(oldOutput);
        }
        var text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("central-1"), text);
        assertTrue(text.contains("campus-2"), text);
        assertTrue(text.contains("ELECTRIC_SCOOTER"), text);
    }
}
