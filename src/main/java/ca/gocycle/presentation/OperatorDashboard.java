package ca.gocycle.presentation;

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
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public class OperatorDashboard implements FaultObserver {

    private final StationService stationService;

    private List<FaultReport> faultReports;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public OperatorDashboard(StationService stationService) {
        this.stationService = stationService;
        this.faultReports = new ArrayList<>();
    }

    /**
     * Receive the complete report; notifier/dashboard failures must not prevent delivery to other observers.
     * @param report required input; validate malformed values before mutation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public void onFault(FaultReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Fault report is required");
        }
        faultReports.add(report);
    }

    /**
     * Show shared station/vehicle state and visible unresolved faults to the simulated operator.
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public String showNetworkState() {
        StringBuilder output = new StringBuilder("Network state\nStations:\n");
        for (var station : stationService.listStations()) {
            output.append("- ").append(station.getName())
                    .append(" (").append(station.getId()).append(")")
                    .append(station.getOpen() ? " open" : " closed")
                    .append('\n');
        }
        output.append("Vehicles:\n");
        for (var vehicle : stationService.listVehicles()) {
            output.append("- ").append(vehicle.getId()).append(" ")
                    .append(vehicle.getType()).append(" ")
                    .append(vehicle.getAvailability()).append(" ")
                    .append(vehicle.getServiceStatus()).append('\n');
        }
        output.append("Unresolved faults:\n");
        for (var report : faultReports) {
            if (report.getResolvedAt() == null) {
                output.append("- ").append(report.getType()).append(": ")
                        .append(report.getDescription()).append('\n');
            }
        }
        return output.toString();
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public StationService getStationService() {
        return stationService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public List<FaultReport> getFaultReports() {
        return List.copyOf(faultReports);
    }
}
