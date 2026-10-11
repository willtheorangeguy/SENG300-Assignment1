package ca.gocycle.fault;

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
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public class MaintenanceNotifier implements FaultObserver {

    private List<FaultReport> receivedReports;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public MaintenanceNotifier() {
        this.receivedReports = new ArrayList<>();
    }

    /**
     * Receive the complete report; notifier/dashboard failures must not prevent delivery to other observers.
     * @param report required input; validate malformed values before mutation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public void onFault(FaultReport report) {
        throw new UnsupportedOperationException("TODO: implement MaintenanceNotifier.onFault");
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public List<FaultReport> getReceivedReports() {
        return List.copyOf(receivedReports);
    }
}
