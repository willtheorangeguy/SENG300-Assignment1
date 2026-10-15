package ca.gocycle.domain.station;

import ca.gocycle.domain.VehicleType;
import ca.gocycle.domain.vehicle.Vehicle;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One physical space, independent of its occupant's service status. */
public class DockingPoint {
    private final String id;
    private final boolean usable;
    private final Set<VehicleType> acceptedTypes;
    private Vehicle vehicle;
    private boolean lockConfirmed;
    // Composition ownership is assigned once by Station, under the shared ownership lock.
    private Station owner;

    /** @throws IllegalArgumentException for malformed attributes or inconsistent initial occupancy */
    public DockingPoint(String id, boolean usable, Set<VehicleType> acceptedTypes, Vehicle vehicle, boolean lockConfirmed) {
        if (id == null || id.isBlank() || acceptedTypes == null || acceptedTypes.isEmpty()
                || acceptedTypes.stream().anyMatch(type -> type == null)) {
            throw new IllegalArgumentException("Dock requires an ID and nonempty accepted vehicle types");
        }
        if ((vehicle == null && lockConfirmed) || (vehicle != null && !acceptedTypes.contains(vehicle.getType()))) {
            throw new IllegalArgumentException("Initial vehicle and lock must be compatible with the dock");
        }
        this.id = id;
        this.usable = usable;
        this.acceptedTypes = Set.copyOf(acceptedTypes);
        this.vehicle = vehicle;
        this.lockConfirmed = lockConfirmed;
    }

    /** @throws IllegalArgumentException if type is null */
    public synchronized boolean canAccept(VehicleType type) {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        return usable && vehicle == null && acceptedTypes.contains(type);
    }

    /**
     * Records physical attachment only; the trip service decides when a ride ends.
     * @throws IllegalArgumentException if the vehicle is null
     * @throws IllegalStateException if unusable, occupied or incompatible
     */
    public synchronized void attach(Vehicle vehicle, boolean confirmed) {
        if (vehicle == null) throw new IllegalArgumentException("Vehicle is required");
        if (!canAccept(vehicle.getType())) throw new IllegalStateException("Dock cannot accept this vehicle");
        this.vehicle = vehicle;
        lockConfirmed = confirmed;
    }

    /** Caller confirms unlocking first. @throws IllegalStateException if already empty */
    public synchronized Vehicle detach() {
        if (vehicle == null) throw new IllegalStateException("Dock is empty");
        Vehicle detached = vehicle;
        vehicle = null;
        lockConfirmed = false;
        return detached;
    }

    /**
     * Records a matching hardware confirmation without changing trip or vehicle status.
     * @throws IllegalArgumentException if the ID is null
     * @throws IllegalStateException if empty or occupied by a different vehicle
     */
    public synchronized void confirmLock(UUID vehicleId) {
        if (vehicleId == null) throw new IllegalArgumentException("Vehicle ID is required");
        if (vehicle == null || !vehicle.getId().equals(vehicleId)) {
            throw new IllegalStateException("Lock confirmation does not match the dock occupant");
        }
        lockConfirmed = true;
    }

    /** Validate the entire list before assigning any ownership, even across concurrent constructors. */
    static void claimForStation(List<DockingPoint> docks, Station station) {
        synchronized (DockingPoint.class) {
            for (DockingPoint dock : docks) {
                if (dock.owner != null) throw new IllegalArgumentException("Dock already belongs to a station: " + dock.id);
            }
            for (DockingPoint dock : docks) dock.owner = station;
        }
    }

    public String getId() { return id; }
    public boolean getUsable() { return usable; }
    public Set<VehicleType> getAcceptedTypes() { return acceptedTypes; }
    public synchronized Vehicle getVehicle() { return vehicle; }
    public synchronized boolean getLockConfirmed() { return lockConfirmed; }
}
