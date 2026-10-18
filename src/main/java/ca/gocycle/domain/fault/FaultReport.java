package ca.gocycle.domain.fault;

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
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public class FaultReport {

    private final UUID id;
    /** Nullable until supplied/recorded; see the operation contract. */
    private final Vehicle vehicle;

    private final FaultSource source;

    private final FaultType type;

    private String description;

    private final Instant reportedAt;
    /** Nullable until supplied/recorded; see the operation contract. */
    private Instant resolvedAt;
    /** Nullable until supplied/recorded; see the operation contract. */
    private String resolution;

    /**
     * Create an open report, or restore a resolved one when both resolution values are supplied.
     * @throws IllegalArgumentException if a required value is missing, the description is blank, only one
     *         resolution value is supplied, the resolution note is blank, or it was resolved before it was reported
     */
    public FaultReport(UUID id, Vehicle vehicle, FaultSource source, FaultType type, String description, Instant reportedAt, Instant resolvedAt, String resolution) {
        if (id == null || source == null || type == null || reportedAt == null) {
            throw new IllegalArgumentException("Fault report requires an ID, source, type and report time");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Fault description is required");
        }
        if ((resolvedAt == null) != (resolution == null)) {
            throw new IllegalArgumentException("Resolution time and note must be supplied together");
        }
        if (resolvedAt != null) {
            validateResolution(reportedAt, resolvedAt, resolution);
        }
        this.id = id;
        this.vehicle = vehicle;
        this.source = source;
        this.type = type;
        this.description = description;
        this.reportedAt = reportedAt;
        this.resolvedAt = resolvedAt;
        this.resolution = resolution;
    }

    /**
     * Record resolution time/note once according to operator authority; reject invalid times or missing required notes.
     * Whether the caller may resolve faults, and putting the vehicle back in service, belong to the services.
     * @param at time the fault was resolved; must not be before it was reported
     * @param note what was done to fix it
     * @throws IllegalArgumentException if the time or note is missing, or the time is before the report
     * @throws IllegalStateException if the report is already resolved
     */
    public synchronized void resolve(Instant at, String note) {
        if (at == null) {
            throw new IllegalArgumentException("Resolution time is required");
        }
        validateResolution(reportedAt, at, note);
        if (resolvedAt != null) {
            throw new IllegalStateException("Fault report " + id + " is already resolved");
        }
        resolvedAt = at;
        resolution = note;
    }

    private static void validateResolution(Instant reportedAt, Instant at, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A resolution note is required");
        }
        if (at.isBefore(reportedAt)) {
            throw new IllegalArgumentException("A fault cannot be resolved before it was reported");
        }
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public UUID getId() {
        return id;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Vehicle getVehicle() {
        return vehicle;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public FaultSource getSource() {
        return source;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public FaultType getType() {
        return type;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getDescription() {
        return description;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Instant getReportedAt() {
        return reportedAt;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized Instant getResolvedAt() {
        return resolvedAt;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized String getResolution() {
        return resolution;
    }
}
