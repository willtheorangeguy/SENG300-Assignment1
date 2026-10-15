package ca.gocycle.domain;

import ca.gocycle.data.DisplayConfiguration;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.vehicle.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StationDockingPointTest {
    private PedalBike bike() {
        return new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE);
    }
    private DockingPoint dock(String id) {
        return new DockingPoint(id, true, EnumSet.allOf(VehicleType.class), null, false);
    }
    private Zone zone() {
        return new Zone("central", Set.of(PaymentMethod.CREDIT_CARD), new DisplayConfiguration("#000000", "#FFFFFF", "logo.svg"));
    }
    private Station station(boolean open, List<DockingPoint> docks) {
        return new Station("station", "Station", 51, -114, zone(), open, docks);
    }

    @Test void attachConfirmAndDetachPreservePhysicalOccupancyAndRideState() {
        var d = dock("dock");
        var v = bike();
        v.reserve();
        v.markRented();
        d.attach(v, false);
        assertSame(v, d.getVehicle());
        assertFalse(d.getLockConfirmed());
        assertFalse(d.canAccept(VehicleType.PEDAL_BIKE));
        assertThrows(IllegalStateException.class, () -> d.attach(bike(), true));
        assertThrows(IllegalStateException.class, () -> d.confirmLock(UUID.randomUUID()));
        assertFalse(d.getLockConfirmed());
        d.confirmLock(v.getId());
        d.confirmLock(v.getId());
        assertTrue(d.getLockConfirmed());
        assertEquals(VehicleAvailability.RENTED, v.getAvailability());
        // Only the coordinating trip service records the confirmed return.
        v.markDocked();
        assertSame(v, d.detach());
        assertNull(d.getVehicle());
        assertFalse(d.getLockConfirmed());
        assertTrue(d.canAccept(VehicleType.PEDAL_BIKE));
        assertThrows(IllegalStateException.class, d::detach);
        assertThrows(IllegalStateException.class, () -> d.confirmLock(v.getId()));
    }

    @Test void unusableAndIncompatibleDocksRejectWithoutMutation() {
        var d = new DockingPoint("electric", true, Set.of(VehicleType.ELECTRIC_BIKE), null, false);
        assertFalse(d.canAccept(VehicleType.PEDAL_BIKE));
        assertThrows(IllegalStateException.class, () -> d.attach(bike(), true));
        assertNull(d.getVehicle());
        var unavailable = new DockingPoint("unusable", false, Set.of(VehicleType.PEDAL_BIKE), null, false);
        assertFalse(unavailable.canAccept(VehicleType.PEDAL_BIKE));
        assertThrows(IllegalStateException.class, () -> unavailable.attach(bike(), true));
        assertNull(unavailable.getVehicle());
    }

    @Test void brokenVehiclesAndUnconfirmedAttachmentsStillOccupySpace() {
        var v = bike();
        v.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);
        var occupied = new DockingPoint("broken", false, Set.of(VehicleType.PEDAL_BIKE), v, true);
        var empty = dock("empty");
        var s = station(true, List.of(occupied, empty));
        assertEquals(0.5, s.occupancyRatio());
        assertSame(empty, s.findFreeDock(VehicleType.PEDAL_BIKE).orElseThrow());
        empty.attach(bike(), false);
        assertEquals(1.0, s.occupancyRatio());
        assertTrue(s.findFreeDock(VehicleType.PEDAL_BIKE).isEmpty());
        occupied.detach();
        assertEquals(0.5, s.occupancyRatio());
        assertTrue(s.findFreeDock(VehicleType.PEDAL_BIKE).isEmpty());
    }

    @Test void stationSearchFiltersUnavailableDocksInConfiguredOrder() {
        var incompatible = new DockingPoint("electric", true, Set.of(VehicleType.ELECTRIC_SCOOTER), null, false);
        var unusable = new DockingPoint("unusable", false, Set.of(VehicleType.PEDAL_BIKE), null, false);
        var occupied = new DockingPoint("occupied", true, Set.of(VehicleType.PEDAL_BIKE), bike(), true);
        var first = dock("first");
        var second = dock("second");
        var s = station(true, List.of(incompatible, unusable, occupied, first, second));
        assertSame(first, s.findFreeDock(VehicleType.PEDAL_BIKE).orElseThrow());
        first.attach(bike(), true);
        assertSame(second, s.findFreeDock(VehicleType.PEDAL_BIKE).orElseThrow());
        assertTrue(station(false, List.of(dock("closed"))).findFreeDock(VehicleType.PEDAL_BIKE).isEmpty());
        assertTrue(station(true, List.of()).findFreeDock(VehicleType.PEDAL_BIKE).isEmpty());
        assertEquals(0.0, station(true, List.of()).occupancyRatio());
    }

    @Test void configurationCollectionsAreDefensiveAndImmutable() {
        var types = EnumSet.of(VehicleType.PEDAL_BIKE);
        var d = new DockingPoint("dock", true, types, null, false);
        types.clear();
        assertTrue(d.canAccept(VehicleType.PEDAL_BIKE));
        assertThrows(UnsupportedOperationException.class, () -> d.getAcceptedTypes().clear());
        var docks = new ArrayList<>(List.of(d));
        var s = station(true, docks);
        docks.clear();
        assertEquals(List.of(d), s.getDockingPoints());
        assertThrows(UnsupportedOperationException.class, () -> s.getDockingPoints().clear());
    }

    @Test void stationOwnershipIsExclusiveAndFailedConstructionDoesNotClaimOtherDocks() {
        var shared = dock("shared");
        var original = station(true, List.of(shared));
        var unclaimed = dock("unclaimed");
        assertThrows(IllegalArgumentException.class, () -> station(true, List.of(unclaimed, shared)));
        assertDoesNotThrow(() -> station(true, List.of(unclaimed)));
        assertEquals(List.of(shared), original.getDockingPoints());
        assertThrows(IllegalArgumentException.class, () -> station(true, List.of(dock("same"), dock("same"))));
        var duplicate = dock("duplicate");
        assertThrows(IllegalArgumentException.class, () -> station(true, List.of(duplicate, duplicate)));
        assertDoesNotThrow(() -> station(true, List.of(duplicate)));
    }

    @Test void twoConcurrentAttachmentsCannotOverwriteEachOther() throws Exception {
        var d = dock("dock");
        var firstBike = bike();
        var secondBike = bike();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attachAfter(start, d, firstBike));
            var second = pool.submit(() -> attachAfter(start, d, secondBike));
            start.countDown();
            boolean firstWon = first.get(5, TimeUnit.SECONDS);
            assertNotEquals(firstWon, second.get(5, TimeUnit.SECONDS));
            assertSame(firstWon ? firstBike : secondBike, d.getVehicle());
        }
    }

    private boolean attachAfter(CountDownLatch start, DockingPoint dock, Vehicle vehicle) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
        try { dock.attach(vehicle, true); return true; } catch (IllegalStateException expected) { return false; }
    }

    @Test void invalidDockInputsRejectWithoutChangingOccupant() {
        assertThrows(IllegalArgumentException.class, () -> new DockingPoint(" ", true, Set.of(VehicleType.PEDAL_BIKE), null, false));
        assertThrows(IllegalArgumentException.class, () -> new DockingPoint("d", true, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new DockingPoint("d", true, Set.of(), null, false));
        assertThrows(IllegalArgumentException.class, () -> new DockingPoint("d", true, new HashSet<>(Arrays.asList((VehicleType) null)), null, false));
        assertThrows(IllegalArgumentException.class, () -> new DockingPoint("d", true, Set.of(VehicleType.PEDAL_BIKE), null, true));
        assertThrows(IllegalArgumentException.class, () -> new DockingPoint("d", true, Set.of(VehicleType.ELECTRIC_BIKE), bike(), true));
        var d = dock("d");
        assertThrows(IllegalArgumentException.class, () -> d.attach(null, true));
        assertThrows(IllegalArgumentException.class, () -> d.canAccept(null));
        assertThrows(IllegalArgumentException.class, () -> d.confirmLock(null));
        assertNull(d.getVehicle());
    }

    @Test void invalidStationCoordinatesAndAttributesReject() {
        for (double latitude : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -91, 91}) {
            assertThrows(IllegalArgumentException.class, () -> new Station("s", "S", latitude, 0, zone(), true, List.of()));
        }
        for (double longitude : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, -181, 181}) {
            assertThrows(IllegalArgumentException.class, () -> new Station("s", "S", 0, longitude, zone(), true, List.of()));
        }
        assertDoesNotThrow(() -> new Station("s", "S", 90, -180, zone(), true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Station("", "S", 0, 0, zone(), true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Station("s", null, 0, 0, zone(), true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Station("s", "S", 0, 0, null, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> station(true, null));
        assertThrows(IllegalArgumentException.class, () -> station(true, Arrays.asList((DockingPoint) null)));
        assertThrows(IllegalArgumentException.class, () -> station(true, List.of()).findFreeDock(null));
    }
}
