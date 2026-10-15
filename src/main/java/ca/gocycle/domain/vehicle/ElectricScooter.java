package ca.gocycle.domain.vehicle;

import ca.gocycle.domain.VehicleAvailability;
import ca.gocycle.domain.VehicleServiceStatus;
import ca.gocycle.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Concrete ElectricScooter using the shared eligibility and transition rules. */
public class ElectricScooter extends ElectricVehicle {
    public ElectricScooter(UUID id, VehicleServiceStatus serviceStatus, VehicleAvailability availability, Integer batteryPercent, Instant batteryObservedAt, long batteryMaxAgeSeconds) {
        super(id, VehicleType.ELECTRIC_SCOOTER, serviceStatus, availability, batteryPercent, batteryObservedAt, batteryMaxAgeSeconds);
    }

    @Override
    public boolean isRentable(Instant at) {
        return super.isRentable(at);
    }
}
