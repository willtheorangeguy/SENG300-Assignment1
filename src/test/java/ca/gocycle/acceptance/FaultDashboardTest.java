package ca.gocycle.acceptance;

import ca.gocycle.application.StationService;
import ca.gocycle.domain.FaultSource;
import ca.gocycle.domain.FaultType;
import ca.gocycle.domain.fault.FaultReport;
import ca.gocycle.domain.station.Station;
import ca.gocycle.fault.MaintenanceNotifier;
import ca.gocycle.presentation.OperatorDashboard;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class FaultDashboardTest {
    @Test
    void maintenanceNotifierStoresReportsAndReturnsSnapshot() {
        var notifier = new MaintenanceNotifier();
        var report = report();
        notifier.onFault(report);
        assertEquals(List.of(report), notifier.getReceivedReports());
        assertThrows(UnsupportedOperationException.class, () -> notifier.getReceivedReports().clear());
        assertThrows(IllegalArgumentException.class, () -> notifier.onFault(null));
    }

    @Test
    void dashboardStoresFaultReports() {
        var station = Fixtures.station("central", 51.0, -114.0, true, List.of());
        var stations = new StationService(Map.of("central", station), Map.of(), null,
                Clock.systemUTC(), new ReentrantLock()) {
            @Override public List<Station> listStations() { return List.of(station); }
            @Override public List<ca.gocycle.domain.vehicle.Vehicle> listVehicles() { return List.of(); }
        };
        var dashboard = new OperatorDashboard(stations);
        var report = report();
        dashboard.onFault(report);
        assertSame(stations, dashboard.getStationService());
        assertEquals(List.of(report), dashboard.getFaultReports());
        assertThrows(UnsupportedOperationException.class, () -> dashboard.getFaultReports().clear());
        assertThrows(IllegalArgumentException.class, () -> dashboard.onFault(null));
        var state = dashboard.showNetworkState();
        assertTrue(state.contains("Network state"));
        assertTrue(state.contains("central"));
        assertTrue(state.contains("Unresolved faults"));
        assertTrue(state.contains("Flat tire"));
    }

    private FaultReport report() {
        return new FaultReport(UUID.randomUUID(), null, FaultSource.RIDER, FaultType.FLAT_TIRE,
                "Flat tire", Instant.parse("2026-10-01T12:00:00Z"), null, null);
    }
}
