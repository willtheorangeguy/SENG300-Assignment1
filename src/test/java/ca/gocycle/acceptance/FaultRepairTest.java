package ca.gocycle.acceptance;

import ca.gocycle.application.FaultService;
import ca.gocycle.domain.*;
import ca.gocycle.fault.MaintenanceNotifier;
import ca.gocycle.presentation.OperatorDashboard;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static ca.gocycle.acceptance.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class FaultRepairTest {
    @Test void repairClearsOpenReportsAndARecurringFaultBlocksRentalsAgain() throws Exception {
        var network = new Network();
        var dashboard = new OperatorDashboard(network.stations);
        var maintenance = new MaintenanceNotifier();
        var faults = new FaultService(List.of(dashboard, maintenance), network.stations,
                network.config, network.clock);
        var first = faults.reportProblem(network.vehicle.getId(), FaultType.FLAT_TIRE, "Flat tire");
        var second = faults.reportProblem(network.vehicle.getId(), FaultType.BROKEN_BRAKES, "Broken brakes");
        network.clock.advance(Duration.ofMinutes(5));
        faults.changeServiceStatus(network.vehicle.getId(), VehicleServiceStatus.IN_SERVICE, "Tire and brakes repaired");
        assertEquals(network.clock.instant(), first.getResolvedAt());
        assertEquals(network.clock.instant(), second.getResolvedAt());
        assertEquals("Tire and brakes repaired", first.getResolution());
        assertFalse(dashboard.showNetworkState().contains("Flat tire"));
        assertTrue(network.vehicle.isRentable(network.clock.instant()));

        var recurring = faults.reportProblem(network.vehicle.getId(), FaultType.FLAT_TIRE, "Flat again");
        assertNotEquals(first.getId(), recurring.getId());
        assertNull(recurring.getResolvedAt());
        assertFalse(network.vehicle.isRentable(network.clock.instant()));
        assertTrue(dashboard.showNetworkState().contains("Flat again"));
        assertEquals(3, maintenance.getReceivedReports().size());
        assertSame(recurring, faults.reportProblem(network.vehicle.getId(), FaultType.FLAT_TIRE, "Duplicate"));
        assertEquals(3, maintenance.getReceivedReports().size());
    }

    @Test void invalidRepairDoesNotClearReportsOrRestoreService() throws Exception {
        var network = new Network();
        var faults = new FaultService(List.of(), network.stations, network.config, network.clock);
        var report = faults.reportProblem(network.vehicle.getId(), FaultType.FLAT_TIRE, "Flat tire");
        assertThrows(IllegalArgumentException.class, () -> faults.changeServiceStatus(
                network.vehicle.getId(), VehicleServiceStatus.IN_SERVICE, " "));
        assertNull(report.getResolvedAt());
        assertEquals(VehicleServiceStatus.OUT_OF_SERVICE, network.vehicle.getServiceStatus());
        faults.changeServiceStatus(network.vehicle.getId(), VehicleServiceStatus.OUT_OF_SERVICE, null);
        assertNull(report.getResolvedAt());
    }
}
