package ca.gocycle.domain.station;

import ca.gocycle.domain.VehicleType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** A station exclusively owns its docking points; vehicle faults do not free physical capacity. */
public class Station {
    private final String id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final Zone zone;
    private final boolean open;
    private final List<DockingPoint> dockingPoints;

    /**
     * Copies the list and claims each dock once. Empty stations have zero occupancy.
     * @throws IllegalArgumentException for malformed coordinates, missing attributes,
     * duplicate dock IDs or docks already owned by another station
     */
    public Station(String id, String name, double latitude, double longitude, Zone zone,
            boolean open, List<DockingPoint> dockingPoints) {
        if (id == null || id.isBlank() || name == null || name.isBlank() || zone == null || dockingPoints == null) {
            throw new IllegalArgumentException("Station ID, name, zone and docking points are required");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Station coordinates must be finite and within geographic bounds");
        }
        var ids = new HashSet<String>();
        for (DockingPoint dock : dockingPoints) {
            if (dock == null || !ids.add(dock.getId())) {
                throw new IllegalArgumentException("Docking points must be non-null with unique IDs");
            }
        }
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zone = zone;
        this.open = open;
        this.dockingPoints = List.copyOf(dockingPoints);
        DockingPoint.claimForStation(this.dockingPoints, this);
    }

    /**
     * Returns the first usable compatible empty dock in configured order, or empty if closed/full.
     * This is a lookup, not a reservation; attach rechecks capacity atomically.
     * @throws IllegalArgumentException if type is null
     */
    public Optional<DockingPoint> findFreeDock(VehicleType type) {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        if (!open) return Optional.empty();
        return dockingPoints.stream().filter(dock -> dock.canAccept(type)).findFirst();
    }

    /** Occupied/total physical spaces, including unusable docks and broken occupants; empty is 0. */
    public double occupancyRatio() {
        if (dockingPoints.isEmpty()) return 0.0;
        long occupied = dockingPoints.stream().filter(dock -> dock.getVehicle() != null).count();
        return (double) occupied / dockingPoints.size();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Zone getZone() { return zone; }
    public boolean getOpen() { return open; }
    /** Immutable list snapshot; contained docks remain live domain objects owned by this station. */
    public List<DockingPoint> getDockingPoints() { return dockingPoints; }
}
