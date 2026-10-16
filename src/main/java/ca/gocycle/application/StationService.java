package ca.gocycle.application;

import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ca.gocycle.config.*;
import ca.gocycle.data.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.fault.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;

public class StationService {
    private final Map<String, Station> stations;
    private final Map<UUID, Vehicle> vehicles;
    private final DeploymentConfiguration configuration;
    private final Clock clock;
    private final ReentrantLock coordinationLock;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public StationService(Map<String, Station> stations, Map<UUID, Vehicle> vehicles, DeploymentConfiguration configuration, Clock clock, ReentrantLock coordinationLock) {
        this.stations = new LinkedHashMap<>(stations);
        this.vehicles = new LinkedHashMap<>(vehicles);
        this.configuration = configuration;
        this.clock = clock;
        this.coordinationLock = coordinationLock;
    }

    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking mutable stores.
     * @param id required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<Vehicle> findVehicle(UUID id) {

    if (id == null) {
        throw new IllegalArgumentException("Vehicle ID cannot be null");
    }

    return Optional.ofNullable(vehicles.get(id));
}

    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking mutable stores.
     * @param id required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<DockingPoint> findVehicleDock(UUID id) {

    if (id == null) {
        throw new IllegalArgumentException("Vehicle ID cannot be null");
    }

    for (Station station : stations.values()) {
        for (DockingPoint dock : station.getDockingPoints()) {

            Vehicle vehicle = dock.getVehicle();

            if (vehicle != null && vehicle.getId().equals(id)) {
                return Optional.of(dock);
            }
        }
    }

    return Optional.empty();
}
    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking mutable stores.
     * @param id required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<Station> findStation(String id) {

    if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Station ID cannot be blank");
    }

    return Optional.ofNullable(stations.get(id));
}

    /**
     * Use geographic straight-line distance with stable station-ID tie break; exclude closed/full/unusable/incompatible stations.
     * @param fromId required input; validate malformed values before mutation
     * @param type required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<Station> nearestAvailable(String fromId, VehicleType type) {

    if (fromId == null || fromId.isBlank() || type == null) {
        throw new IllegalArgumentException("Invalid station ID or vehicle type");
    }

    Station startingStation = stations.get(fromId);

    if (startingStation == null) {
        throw new NoSuchElementException("Station not found");
    }

    Station nearest = null;
    double shortestDistance = Double.MAX_VALUE;

    for (Station station : stations.values()) {

        if (station.getId().equals(fromId)) {
            continue;
        }

        if (station.findFreeDock(type).isEmpty()) {
            continue;
        }

double latitude1 = Math.toRadians(startingStation.getLatitude());
double latitude2 = Math.toRadians(station.getLatitude());

double latitudeDifference = latitude2 - latitude1;

double longitudeDifference = Math.toRadians(
        station.getLongitude() - startingStation.getLongitude());

double a = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
        + Math.cos(latitude1) * Math.cos(latitude2)
        * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);

double distance = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        if (nearest == null || distance < shortestDistance
                || (distance == shortestDistance
                && station.getId().compareTo(nearest.getId()) < 0)) {

            nearest = station;
            shortestDistance = distance;
        }
    }

    return Optional.ofNullable(nearest);
}

    /**
     * Only relocate a docked vehicle; validate destination capacity/type before source detach and preserve source on failure.
     * @param vehicleId required input; validate malformed values before mutation
     * @param destinationId required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public void relocate(UUID vehicleId, String destinationId) {

    if (vehicleId == null || destinationId == null || destinationId.isBlank()) {
        throw new IllegalArgumentException("Invalid vehicle or destination ID");
    }

    coordinationLock.lock();

    try {
        Vehicle vehicle = findVehicle(vehicleId)
                .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));

        Station destination = findStation(destinationId)
                .orElseThrow(() -> new NoSuchElementException("Destination station not found"));

        DockingPoint sourceDock = findVehicleDock(vehicleId)
                .orElseThrow(() -> new IllegalStateException("Vehicle is not docked"));

        DockingPoint destinationDock = destination.findFreeDock(vehicle.getType())
                .orElseThrow(() -> new IllegalStateException("Destination has no available dock"));

        if (!sourceDock.getLockConfirmed()) {
            throw new IllegalStateException("Vehicle is not securely docked");
        }

sourceDock.detach();

try {
    destinationDock.attach(vehicle, true);
} catch (RuntimeException e) {
    sourceDock.attach(vehicle, true);
    throw e;
}

    } finally {
        coordinationLock.unlock();
    }
}

    /**
     * Validate nonempty methods and complete branding; affect future screens/trips while preserving active snapshots.
     * @param zoneId required input; validate malformed values before mutation
     * @param methods required input; validate malformed values before mutation
     * @param display required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public void updateZone(String zoneId, Set<PaymentMethod> methods, DisplayConfiguration display) {

    if (zoneId == null || zoneId.isBlank() ||
            methods == null || methods.isEmpty() || display == null) {
        throw new IllegalArgumentException("Invalid zone configuration");
    }

    if (methods.contains(null)) {
        throw new IllegalArgumentException("Payment methods cannot contain null");
    }

    coordinationLock.lock();

    try {
        for (Station station : stations.values()) {
            Zone zone = station.getZone();

            if (zone != null && zone.getId().equals(zoneId)) {
                zone.updateConfiguration(methods, display);
                return;
            }
        }

        throw new NoSuchElementException("Zone not found");

    } finally {
        coordinationLock.unlock();
    }
}

    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking mutable stores.
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public List<Station> listStations() {
    return List.copyOf(stations.values());
}
    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking mutable stores.
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public List<Vehicle> listVehicles() {
    return List.copyOf(vehicles.values());
}

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public DeploymentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Clock getClock() {
        return clock;
    }
}
