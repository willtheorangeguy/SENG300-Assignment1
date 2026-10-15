package ca.gocycle.domain.vehicle;

import ca.gocycle.domain.VehicleAvailability;
import ca.gocycle.domain.VehicleServiceStatus;
import ca.gocycle.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Concrete PedalBike using the shared eligibility and transition rules. */
public class PedalBike extends Vehicle {
    public PedalBike(UUID id, VehicleServiceStatus serviceStatus, VehicleAvailability availability) {
        super(id, VehicleType.PEDAL_BIKE, serviceStatus, availability);
    }

    @Override
    public boolean isRentable(Instant at) {
        return super.isRentable(at);
    }
}
