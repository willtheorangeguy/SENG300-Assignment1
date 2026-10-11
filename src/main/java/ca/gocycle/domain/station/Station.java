package ca.gocycle.domain.station;

import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ca.gocycle.application.*;
import ca.gocycle.config.*;
import ca.gocycle.data.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.fault.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public class Station {

    private final String id;

    private final String name;

    private double latitude;

    private double longitude;

    private Zone zone;

    private boolean open;

    private List<DockingPoint> dockingPoints;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public Station(String id, String name, double latitude, double longitude, Zone zone, boolean open, List<DockingPoint> dockingPoints) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zone = zone;
        this.open = open;
        this.dockingPoints = new ArrayList<>(dockingPoints);
    }

    /**
     * Exclude closed stations and unusable, occupied or incompatible docks.
     * @param type required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public Optional<DockingPoint> findFreeDock(VehicleType type) {
        throw new UnsupportedOperationException("TODO: implement Station.findFreeDock");
    }

    /**
     * Return occupied docking points divided by capacity, including broken vehicles.
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public double occupancyRatio() {
        throw new UnsupportedOperationException("TODO: implement Station.occupancyRatio");
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getId() {
        return id;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getName() {
        return name;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Zone getZone() {
        return zone;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public boolean getOpen() {
        return open;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public List<DockingPoint> getDockingPoints() {
        return List.copyOf(dockingPoints);
    }
}
