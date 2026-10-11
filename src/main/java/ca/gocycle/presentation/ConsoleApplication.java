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


public class ConsoleApplication {

    private DeploymentConfiguration configuration;

    private RiderService riderService;

    private TripService tripService;

    private FaultService faultService;

    private final StationService stationService;

    private OperatorDashboard dashboard;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public ConsoleApplication(DeploymentConfiguration configuration, RiderService riderService, TripService tripService, FaultService faultService, StationService stationService, OperatorDashboard dashboard) {
        this.configuration = configuration;
        this.riderService = riderService;
        this.tripService = tripService;
        this.faultService = faultService;
        this.stationService = stationService;
        this.dashboard = dashboard;
    }

    /**
     * Select --config PATH over GOCYCLE_CONFIG over the example, then validate before wiring shared services, Clock, lock, strategies, observers and simulators. Startup remains TODO.
     * @param args required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public static void main(String[] args) {
        throw new UnsupportedOperationException("TODO: implement ConsoleApplication.main");
    }

    /**
     * Provide registration/start/return/fault/operator flows in at least en/fr, zone branding, saved preferences and visible simulated audio.
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public void run() {
        throw new UnsupportedOperationException("TODO: implement ConsoleApplication.run");
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
    public RiderService getRiderService() {
        return riderService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public TripService getTripService() {
        return tripService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public FaultService getFaultService() {
        return faultService;
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
    public OperatorDashboard getDashboard() {
        return dashboard;
    }
}
