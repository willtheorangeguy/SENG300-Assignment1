package ca.gocycle.config;

import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ca.gocycle.application.*;
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


public class DeploymentConfiguration {
    private final PricingConfiguration pricing;
    private final StudentRules students;
    private final MembershipRules membership;
    private final RiderRules riders;
    private final TripRules trips;
    private final StationRules stations;
    private final FaultRules faults;
    private final OperatorRules operators;
    private final InterfaceRules interfaces;
    private final ReceiptRules receipts;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    @JsonCreator
    public DeploymentConfiguration(@JsonProperty("pricing") PricingConfiguration pricing, @JsonProperty("students") StudentRules students, @JsonProperty("membership") MembershipRules membership, @JsonProperty("riders") RiderRules riders, @JsonProperty("trips") TripRules trips, @JsonProperty("stations") StationRules stations, @JsonProperty("faults") FaultRules faults, @JsonProperty("operators") OperatorRules operators, @JsonProperty("interfaces") InterfaceRules interfaces, @JsonProperty("receipts") ReceiptRules receipts) {
        this.pricing = pricing;
        this.students = students;
        this.membership = membership;
        this.riders = riders;
        this.trips = trips;
        this.stations = stations;
        this.faults = faults;
        this.operators = operators;
        this.interfaces = interfaces;
        this.receipts = receipts;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("pricing")
    public PricingConfiguration getPricing() {
        return pricing;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("students")
    public StudentRules getStudents() {
        return students;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("membership")
    public MembershipRules getMembership() {
        return membership;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("riders")
    public RiderRules getRiders() {
        return riders;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("trips")
    public TripRules getTrips() {
        return trips;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("stations")
    public StationRules getStations() {
        return stations;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("faults")
    public FaultRules getFaults() {
        return faults;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("operators")
    public OperatorRules getOperators() {
        return operators;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("interfaces")
    public InterfaceRules getInterfaces() {
        return interfaces;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("receipts")
    public ReceiptRules getReceipts() {
        return receipts;
    }

    /** Immutable typed settings; JSON property names match record components. */
    public record StudentRules(String emailDomain, Integer ageRestriction, boolean monthlyMembershipAllowed) {}

    /** Immutable typed settings; JSON property names match record components. */
    public record MembershipRules(BigDecimal price, int durationDays, String startsAt) {}

    /** Immutable typed settings; JSON property names match record components. */
    public record RiderRules(boolean accountRequired, List<String> registrationFields, List<String> accountTypes, String stationIdentification, int maximumActiveTrips) {
        public RiderRules {
            registrationFields = List.copyOf(registrationFields);
            accountTypes = List.copyOf(accountTypes);
        }
    }

    /** Immutable typed settings; JSON property names match record components. */
    public record TripRules(String billableTimeStartsAt, int removalTimeoutMinutes, String notRemovedAction, boolean pausingAllowed, int maximumDurationHours, String overdueAction, String lostOrStolenAction, String emptyBatteryAction) {}

    /** Immutable typed settings; JSON property names match record components. */
    public record StationRules(int count, List<Integer> stationsPerZone, int dockingPointsPerStation, boolean allDocksAcceptAllVehicleTypes) {
        public StationRules {
            stationsPerZone = List.copyOf(stationsPerZone);
        }
    }

    /** Immutable typed settings; JSON property names match record components. */
    public record FaultRules(String outOfServiceTiming, boolean reportedBrokenRentable, String duplicateReports) {}

    /** Immutable typed settings; JSON property names match record components. */
    public record OperatorRules(boolean manualTripClosureAllowed, boolean batteryOverrideAllowed, boolean faultOverrideAllowed) {}

    /** Immutable typed settings; JSON property names match record components. */
    public record InterfaceRules(List<String> supportedLanguages, String defaultLanguage, String audioEnabledBy) {
        public InterfaceRules {
            supportedLanguages = List.copyOf(supportedLanguages);
        }
    }

    /** Immutable typed settings; JSON property names match record components. */
    public record ReceiptRules(List<String> destinations, List<String> fields) {
        public ReceiptRules {
            destinations = List.copyOf(destinations);
            fields = List.copyOf(fields);
        }
    }
}
