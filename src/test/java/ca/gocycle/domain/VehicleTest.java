package ca.gocycle.domain;

import ca.gocycle.domain.vehicle.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {
    private static final Instant AT = Instant.parse("2026-10-01T12:00:00Z");
    private PedalBike bike() {
        return new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE);
    }
    static Stream<ElectricVehicle> electricVehicles() {
        return Stream.of(
            new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE, 20, AT, 600),
            new ElectricScooter(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE, 20, AT, 600));
    }

    @ParameterizedTest @EnumSource(VehicleAvailability.class)
    void onlyAvailableInServiceVehiclesAreRentable(VehicleAvailability availability) {
        var v = new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, availability);
        assertEquals(availability == VehicleAvailability.AVAILABLE, v.isRentable(AT));
        v.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);
        assertFalse(v.isRentable(AT));
        assertEquals(availability, v.getAvailability());
    }

    @Test void reservationRentalAndReturnPreserveFaultStatus() {
        var v = bike();
        v.reserve();
        assertThrows(IllegalStateException.class, v::reserve);
        assertThrows(IllegalStateException.class, v::markRelocating);
        assertThrows(IllegalStateException.class, v::markDocked);
        v.markRented();
        v.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);
        assertEquals(VehicleAvailability.RENTED, v.getAvailability());
        assertThrows(IllegalStateException.class, v::releaseReservation);
        assertThrows(IllegalStateException.class, v::markRelocating);
        v.markDocked();
        v.markDocked();
        assertFalse(v.isRentable(AT));
        v.setServiceStatus(VehicleServiceStatus.IN_SERVICE);
        assertTrue(v.isRentable(AT));
    }

    @Test void failedStartReleasesOnlyItsReservation() {
        var v = bike();
        assertThrows(IllegalStateException.class, v::markRented);
        assertThrows(IllegalStateException.class, v::releaseReservation);
        v.reserve();
        v.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);
        v.releaseReservation();
        assertEquals(VehicleAvailability.AVAILABLE, v.getAvailability());
        assertFalse(v.isRentable(AT));
        assertThrows(IllegalStateException.class, v::reserve);
    }

    @Test void brokenVehicleMayBeRelocatedButNotRented() {
        var v = bike();
        v.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);
        v.markRelocating();
        assertThrows(IllegalStateException.class, v::markRelocating);
        v.markDocked();
        assertEquals(VehicleServiceStatus.OUT_OF_SERVICE, v.getServiceStatus());
    }

    @Test void exactlyOneConcurrentReservationSucceeds() throws Exception {
        var v = bike();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> reserve = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
            try { v.reserve(); return true; } catch (IllegalStateException expected) { return false; }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(reserve);
            var second = pool.submit(reserve);
            try { assertTrue(ready.await(5, TimeUnit.SECONDS)); } finally { start.countDown(); }
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(VehicleAvailability.RESERVED, v.getAvailability());
    }

    @ParameterizedTest @MethodSource("electricVehicles")
    void batteryBoundariesAndFreshnessApplyToBothElectricTypes(ElectricVehicle v) {
        assertTrue(v.isRentable(AT));
        assertTrue(v.isRentable(AT.plusSeconds(600)));
        assertFalse(v.isRentable(AT.plusSeconds(600).plusNanos(1)));
        assertFalse(v.isRentable(AT.minusNanos(1)));
        v.updateBattery(19, AT);
        assertFalse(v.isRentable(AT));
        v.updateBattery(100, AT.plusSeconds(1));
        assertTrue(v.isRentable(AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> v.updateBattery(-1, AT.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> v.updateBattery(101, AT.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> v.updateBattery(50, null));
        assertThrows(IllegalArgumentException.class, () -> v.updateBattery(50, AT));
        assertEquals(100, v.getBatteryPercent());
        assertEquals(AT.plusSeconds(1), v.getBatteryObservedAt());
        assertThrows(IllegalArgumentException.class, () -> v.isRentable(null));
    }

    @Test void telemetryDoesNotEndAnActiveRide() {
        var v = new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE,
                VehicleAvailability.RENTED, 80, AT, 600);
        v.updateBattery(0, AT.plusSeconds(1));
        assertEquals(VehicleAvailability.RENTED, v.getAvailability());
        assertEquals(VehicleServiceStatus.IN_SERVICE, v.getServiceStatus());
    }

    @Test void missingReadingCanBeReplacedAndConfiguredAgeIsRespected() {
        var v = new ElectricScooter(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE,
                VehicleAvailability.AVAILABLE, null, null, 3);
        assertFalse(v.isRentable(AT));
        v.updateBattery(20, AT);
        assertTrue(v.isRentable(AT.plusSeconds(3)));
        assertFalse(v.isRentable(AT.plusSeconds(4)));
    }

    @Test void reservationRejectsLowUnknownStaleAndFutureBattery() {
        for (var reading : new Integer[] {19, null, 80}) {
            var v = new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE,
                VehicleAvailability.AVAILABLE, reading, reading == null ? null : Instant.now().minusSeconds(601), 600);
            assertThrows(IllegalStateException.class, v::reserve);
            assertEquals(VehicleAvailability.AVAILABLE, v.getAvailability());
        }
        var future = new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE,
                VehicleAvailability.AVAILABLE, 80, Instant.now().plusSeconds(3600), 600);
        assertThrows(IllegalStateException.class, future::reserve);
    }

    @Test void invalidAttributesFailEarly() {
        assertThrows(IllegalArgumentException.class, () -> new PedalBike(null, VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> new PedalBike(UUID.randomUUID(), null, VehicleAvailability.AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, null));
        var v = bike();
        assertThrows(IllegalArgumentException.class, () -> v.setServiceStatus(null));
        assertThrows(IllegalArgumentException.class, () -> v.isRentable(null));
        assertThrows(IllegalArgumentException.class, () -> new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE, 101, AT, 600));
        assertThrows(IllegalArgumentException.class, () -> new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE, 20, null, 600));
        assertThrows(IllegalArgumentException.class, () -> new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE, null, AT, 600));
        assertThrows(IllegalArgumentException.class, () -> new ElectricBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE, 20, AT, 0));
        assertEquals(VehicleType.PEDAL_BIKE, v.getType());
        assertEquals(VehicleType.ELECTRIC_BIKE, electricVehicles().findFirst().orElseThrow().getType());
        assertEquals(VehicleType.ELECTRIC_SCOOTER, electricVehicles().skip(1).findFirst().orElseThrow().getType());
    }
}
