package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.fault.FaultReport;
import ca.gocycle.domain.vehicle.ElectricBike;
import ca.gocycle.fault.MaintenanceNotifier;
import ca.gocycle.presentation.*;
import ca.gocycle.simulation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static ca.gocycle.acceptance.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BatteryReportingTest {
    @ParameterizedTest @CsvSource({"0,true", "19,true", "20,false", "100,false"})
    void batteryReadingsNotifyOnlyBelowTheCutoff(int battery, boolean low) throws Exception {
        var setup = new Setup();
        var report = setup.faults.reportTelemetry(setup.vehicle.getId(), battery, null, NOW);
        assertEquals(low, report.isPresent());
        assertEquals(battery, setup.vehicle.getBatteryPercent());
        assertEquals(low ? 1 : 0, setup.dashboard.getFaultReports().size());
        assertEquals(low ? 1 : 0, setup.maintenance.getReceivedReports().size());
        assertEquals(!low, setup.vehicle.isRentable(NOW));
        if (low) assertEquals(FaultSource.TELEMETRY, report.orElseThrow().getSource());
    }

    @Test void repeatedLowReadingsDoNotRepeatNotifications() throws Exception {
        var setup = new Setup();
        var first = setup.faults.reportTelemetry(setup.vehicle.getId(), 19, null, NOW).orElseThrow();
        setup.clock.advance(Duration.ofSeconds(1));
        var second = setup.faults.reportTelemetry(setup.vehicle.getId(), 18, null, setup.clock.instant()).orElseThrow();
        assertSame(first, second);
        assertEquals(1, setup.maintenance.getReceivedReports().size());
    }

    @Test void lowBatteryAndAnotherFaultBothReachObservers() throws Exception {
        var setup = new Setup();
        setup.faults.reportTelemetry(setup.vehicle.getId(), 19, FaultType.BROKEN_BRAKES, NOW);
        assertEquals(Set.of(FaultType.LOW_BATTERY, FaultType.BROKEN_BRAKES),
                new HashSet<>(setup.dashboard.getFaultReports().stream().map(FaultReport::getType).toList()));
    }

    @Test void invalidTelemetryDoesNotChangeState() throws Exception {
        var setup = new Setup();
        assertThrows(IllegalArgumentException.class, () -> setup.faults.reportTelemetry(
                setup.vehicle.getId(), 10, null, NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> setup.faults.reportTelemetry(
                setup.vehicle.getId(), -1, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> setup.faults.reportTelemetry(
                setup.vehicle.getId(), 10, null, NOW.minusSeconds(1)));
        assertEquals(80, setup.vehicle.getBatteryPercent());
        assertTrue(setup.dashboard.getFaultReports().isEmpty());
    }

    @Test void consoleBatteryUpdateDoesNotReportAHealthyVehicleAsBroken() throws Exception {
        var setup = new Setup();
        var payments = new PaymentService(new SimulatedPaymentGateway(true, true, true),
                new SimulatedReceiptDelivery(true), setup.configuration, setup.clock);
        var riders = new RiderService(payments, setup.configuration, setup.clock);
        var trips = new TripService(riders, setup.stations, pricing(setup.configuration), payments,
                new SimulatedDockingGateway(true, true), setup.configuration, setup.clock, setup.lock);
        var app = new ConsoleApplication(setup.configuration, riders, trips, setup.faults, setup.stations, setup.dashboard);
        var oldInput = System.in;
        var oldOutput = System.out;
        try {
            System.setIn(new ByteArrayInputStream(("battery\n" + setup.vehicle.getId() + " 100\nquit\n")
                    .getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            app.run();
        } finally {
            System.setIn(oldInput);
            System.setOut(oldOutput);
        }
        assertEquals(100, setup.vehicle.getBatteryPercent());
        assertEquals(VehicleServiceStatus.IN_SERVICE, setup.vehicle.getServiceStatus());
        assertTrue(setup.maintenance.getReceivedReports().isEmpty());
    }

    private static class Setup {
        final ca.gocycle.config.DeploymentConfiguration configuration = config();
        final MutableClock clock = new MutableClock();
        final ReentrantLock lock = new ReentrantLock();
        final ElectricBike vehicle = electric(80, NOW);
        final StationService stations = new StationService(Map.of(), Map.of(vehicle.getId(), vehicle),
                configuration, clock, lock);
        final OperatorDashboard dashboard = new OperatorDashboard(stations);
        final MaintenanceNotifier maintenance = new MaintenanceNotifier();
        final FaultService faults = new FaultService(List.of(dashboard, maintenance), stations, configuration, clock);
        Setup() throws Exception { }
    }
}
