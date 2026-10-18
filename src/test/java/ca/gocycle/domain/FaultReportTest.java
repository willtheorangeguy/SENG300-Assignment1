package ca.gocycle.domain;

import ca.gocycle.domain.fault.FaultReport;
import ca.gocycle.domain.vehicle.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaultReportTest {
    private static final Instant REPORTED = Instant.parse("2026-10-01T12:00:00Z");

    private PedalBike bike() {
        return new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE);
    }
    private FaultReport open(Vehicle v) {
        return new FaultReport(UUID.randomUUID(), v, FaultSource.RIDER, FaultType.FLAT_TIRE, "Front tire is flat", REPORTED, null, null);
    }

    @Test void newReportIsOpenAndKeepsItsDetails() {
        var v = bike();
        var report = open(v);
        assertSame(v, report.getVehicle());
        assertEquals(FaultSource.RIDER, report.getSource());
        assertEquals(FaultType.FLAT_TIRE, report.getType());
        assertEquals("Front tire is flat", report.getDescription());
        assertEquals(REPORTED, report.getReportedAt());
        assertNull(report.getResolvedAt());
        assertNull(report.getResolution());
    }

    @Test void resolveRecordsTimeAndNoteOnce() {
        var report = open(bike());
        var fixedAt = REPORTED.plus(Duration.ofHours(3));
        report.resolve(fixedAt, "Tube replaced");
        assertEquals(fixedAt, report.getResolvedAt());
        assertEquals("Tube replaced", report.getResolution());
        assertThrows(IllegalStateException.class, () -> report.resolve(fixedAt.plusSeconds(60), "Again"));
        assertEquals(fixedAt, report.getResolvedAt());
        assertEquals("Tube replaced", report.getResolution());
    }

    @Test void resolvingAtTheReportInstantIsAllowed() {
        var report = open(bike());
        report.resolve(REPORTED, "False alarm");
        assertEquals(REPORTED, report.getResolvedAt());
    }

    @Test void invalidResolutionLeavesTheReportOpen() {
        var report = open(bike());
        assertThrows(IllegalArgumentException.class, () -> report.resolve(null, "Fixed"));
        assertThrows(IllegalArgumentException.class, () -> report.resolve(REPORTED.plusSeconds(60), null));
        assertThrows(IllegalArgumentException.class, () -> report.resolve(REPORTED.plusSeconds(60), " "));
        assertThrows(IllegalArgumentException.class, () -> report.resolve(REPORTED.minusSeconds(1), "Fixed"));
        assertNull(report.getResolvedAt());
        assertNull(report.getResolution());
    }

    @Test void constructorRejectsMalformedReports() {
        var v = bike();
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(null, v, FaultSource.RIDER, FaultType.FLAT_TIRE, "d", REPORTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, null, FaultType.FLAT_TIRE, "d", REPORTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, FaultSource.RIDER, null, "d", REPORTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, FaultSource.RIDER, FaultType.FLAT_TIRE, " ", REPORTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, FaultSource.RIDER, FaultType.FLAT_TIRE, "d", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, FaultSource.RIDER, FaultType.FLAT_TIRE, "d", REPORTED, REPORTED, null));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, FaultSource.RIDER, FaultType.FLAT_TIRE, "d", REPORTED, null, "note"));
        assertThrows(IllegalArgumentException.class, () -> new FaultReport(id, v, FaultSource.RIDER, FaultType.FLAT_TIRE, "d", REPORTED, REPORTED.minusSeconds(1), "note"));
    }

    @Test void resolvedReportCanBeRestored() {
        var at = REPORTED.plusSeconds(600);
        var report = new FaultReport(UUID.randomUUID(), bike(), FaultSource.TELEMETRY, FaultType.LOW_BATTERY, "Battery 5%", REPORTED, at, "Charged");
        assertEquals(at, report.getResolvedAt());
        assertThrows(IllegalStateException.class, () -> report.resolve(at.plusSeconds(1), "Again"));
    }
}
