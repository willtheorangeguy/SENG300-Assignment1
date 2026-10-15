package ca.gocycle.domain.vehicle;

import ca.gocycle.domain.VehicleAvailability;
import ca.gocycle.domain.VehicleServiceStatus;
import ca.gocycle.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Concrete ElectricBike using the shared eligibility and transition rules. */
public class ElectricBike extends ElectricVehicle {
    public ElectricBike(UUID id, VehicleServiceStatus serviceStatus, VehicleAvailability availability, Integer batteryPercent, Instant batteryObservedAt, long batteryMaxAgeSeconds) {
        super(id, VehicleType.ELECTRIC_BIKE, serviceStatus, availability, batteryPercent, batteryObservedAt, batteryMaxAgeSeconds);
    }

    @Override
    public boolean isRentable(Instant at) {
        return super.isRentable(at);
    }
}
