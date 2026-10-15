package ca.gocycle.domain.vehicle;

import ca.gocycle.domain.VehicleAvailability;
import ca.gocycle.domain.VehicleServiceStatus;
import ca.gocycle.domain.VehicleType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Shared battery eligibility for electric bikes and scooters. */
public abstract class ElectricVehicle extends Vehicle {
    private static final int MINIMUM_RENTAL_BATTERY_PERCENT = 20;
    private Integer batteryPercent;
    private Instant batteryObservedAt;
    private final long batteryMaxAgeSeconds;

    /**
     * A missing reading is represented by two nulls. The maximum age is deployment supplied.
     * @throws IllegalArgumentException for invalid type, percentage, age or incomplete reading
     */
    public ElectricVehicle(UUID id, VehicleType type, VehicleServiceStatus serviceStatus,
            VehicleAvailability availability, Integer batteryPercent, Instant batteryObservedAt,
            long batteryMaxAgeSeconds) {
        super(id, type, serviceStatus, availability);
        if (type == VehicleType.PEDAL_BIKE || batteryMaxAgeSeconds <= 0) {
            throw new IllegalArgumentException("Electric type and positive battery maximum age required");
        }
        if ((batteryPercent == null) != (batteryObservedAt == null)) {
            throw new IllegalArgumentException("Battery percentage and observation time must be supplied together");
        }
        if (batteryPercent != null) validateReading(batteryPercent, batteryObservedAt);
        this.batteryPercent = batteryPercent;
        this.batteryObservedAt = batteryObservedAt;
        this.batteryMaxAgeSeconds = batteryMaxAgeSeconds;
    }

    /**
     * Accepts chronological telemetry, including corrections at the same instant.
     * Never changes service status or ends an active trip.
     * @throws IllegalArgumentException for invalid or out-of-order telemetry
     */
    public synchronized void updateBattery(int percent, Instant observedAt) {
        validateReading(percent, observedAt);
        if (batteryObservedAt != null && observedAt.isBefore(batteryObservedAt)) {
            throw new IllegalArgumentException("Battery observation must not precede the current reading");
        }
        batteryPercent = percent;
        batteryObservedAt = observedAt;
    }

    private static void validateReading(int percent, Instant observedAt) {
        if (percent < 0 || percent > 100 || observedAt == null) {
            throw new IllegalArgumentException("Battery percentage must be 0..100 with an observation time");
        }
    }

    /** Exactly 20% and exactly the configured maximum age pass; future readings do not. */
    @Override
    public synchronized boolean isRentable(Instant at) {
        return super.isRentable(at) && batteryPercent != null
                && batteryPercent >= MINIMUM_RENTAL_BATTERY_PERCENT
                && !batteryObservedAt.isAfter(at)
                && Duration.between(batteryObservedAt, at).compareTo(Duration.ofSeconds(batteryMaxAgeSeconds)) <= 0;
    }

    public synchronized Integer getBatteryPercent() { return batteryPercent; }
    public synchronized Instant getBatteryObservedAt() { return batteryObservedAt; }
    public long getBatteryMaxAgeSeconds() { return batteryMaxAgeSeconds; }
}
