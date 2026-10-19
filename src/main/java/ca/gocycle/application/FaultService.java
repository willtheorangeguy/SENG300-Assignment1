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

public class FaultService {
    private final Map<UUID, FaultReport> reports;
    private final List<FaultObserver> observers;
    private final StationService stations;
    private final DeploymentConfiguration configuration;
    private final Clock clock;
    private final Map<UUID, List<FaultObserver>> failedDeliveries;

    public FaultService(List<FaultObserver> observers, StationService stations, DeploymentConfiguration configuration, Clock clock) {
        this.reports = new LinkedHashMap<>();
        this.observers = new ArrayList<>(observers);
        this.stations = stations;
        this.configuration = configuration;
        this.clock = clock;
        this.failedDeliveries = new LinkedHashMap<>();
    }

    /**
     * Record a known vehicle fault, discard duplicates, block future rentals and notify every observer while preserving active trips.
     * @param vehicleId required input; validate malformed values before mutation
     * @param type required input; validate malformed values before mutation
     * @param description required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public FaultReport reportProblem(UUID vehicleId, FaultType type, String description) {
    return createFaultReport(vehicleId, type, description, FaultSource.RIDER);
}

private synchronized FaultReport createFaultReport(UUID vehicleId, FaultType type,
                                     String description, FaultSource source) {

    if (vehicleId == null || type == null ||
        description == null || description.isBlank()) {
        throw new IllegalArgumentException("Invalid fault report");
    }
    Vehicle vehicle = stations.findVehicle(vehicleId)
        .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));

        for (FaultReport report : reports.values()) {
    if (report.getVehicle().getId().equals(vehicleId)
            && report.getType() == type
            && report.getResolvedAt() == null) {
        return report;
    }
}

FaultReport report = new FaultReport(
    UUID.randomUUID(),
    vehicle,
    source,
    type,
    description,
    clock.instant(),
    null,
    null
);

reports.put(report.getId(), report);
vehicle.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);

publish(report);

return report;
}
    /**
     * Validate telemetry; update battery and emit relevant faults. Preserve active trips and reject invalid values/times.
     * @param vehicleId required input; validate malformed values before mutation
     * @param battery required input; validate malformed values before mutation
     * @param type required input; validate malformed values before mutation
     * @param observedAt required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<FaultReport> reportTelemetry(UUID vehicleId, Integer battery, FaultType type, Instant observedAt) {

    if (vehicleId == null || observedAt == null || observedAt.isAfter(clock.instant()) ||
        (battery == null && type == null)) {
        throw new IllegalArgumentException("Invalid telemetry");
    }
    Vehicle vehicle = stations.findVehicle(vehicleId)
        .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));

if (battery != null) {
    if (!(vehicle instanceof ElectricVehicle)) {
        throw new IllegalArgumentException("Vehicle is not electric");
    }

    ElectricVehicle electric = (ElectricVehicle) vehicle;
    electric.updateBattery(battery, observedAt);
}

Optional<FaultReport> result = Optional.empty();
if (battery != null && battery < 20) {
    result = Optional.of(createFaultReport(vehicleId, FaultType.LOW_BATTERY,
            "Battery below 20%", FaultSource.TELEMETRY));
}

if (type != null) {
    result = Optional.of(createFaultReport(vehicleId, type, "Telemetry fault", FaultSource.TELEMETRY));
}

return result;
}

    /**
     * Require an operator repair note when restoring service; preserve battery cutoff and active trip state.
     * @param vehicleId required input; validate malformed values before mutation
     * @param status required input; validate malformed values before mutation
     * @param repairNote required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public synchronized void changeServiceStatus(UUID vehicleId, VehicleServiceStatus status, String repairNote) {

    if (vehicleId == null || status == null) {
        throw new IllegalArgumentException("Invalid vehicle or status");
    }

    if (status == VehicleServiceStatus.IN_SERVICE &&
        (repairNote == null || repairNote.isBlank())) {
        throw new IllegalArgumentException("Repair note is required");
    }

    Vehicle vehicle = stations.findVehicle(vehicleId)
            .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));

    if (status == VehicleServiceStatus.IN_SERVICE) {
        Instant repairedAt = clock.instant();
        for (FaultReport report : reports.values()) {
            if (report.getVehicle().getId().equals(vehicleId) && report.getResolvedAt() == null) {
                report.resolve(repairedAt, repairNote);
            }
        }
    }
    vehicle.setServiceStatus(status);
}

    /**
     * Register an observer once; do not duplicate notifications.
     * @param observer required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public void subscribe(FaultObserver observer) {

    if (observer == null) {
        throw new IllegalArgumentException("Observer cannot be null");
    }

    if (!observers.contains(observer)) {
        observers.add(observer);
    }
}

    /**
     * Remove the observer registration without changing existing reports.
     * @param observer required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public void unsubscribe(FaultObserver observer) {

    if (observer == null) {
        throw new IllegalArgumentException("Observer cannot be null");
    }

    observers.remove(observer);
}

    /**
     * Attempt every observer even when one fails; retain delivery failures for manual retry without reverting service state.
     * @param report required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */


public void publish(FaultReport report) {

    if (report == null) {
        throw new IllegalArgumentException("Report cannot be null");
    }

    for (FaultObserver observer : observers) {
        try {
            observer.onFault(report);
        } catch (Exception e) {
            failedDeliveries.computeIfAbsent(
                report.getId(), id -> new ArrayList<>()
            ).add(observer);
        }
    }
}
    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public List<FaultObserver> getObservers() {
    return List.copyOf(observers);
}
    public StationService getStations() {
        return stations;
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
