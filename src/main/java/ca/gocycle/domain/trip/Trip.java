package ca.gocycle.domain.trip;

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
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public class Trip {

    private final UUID id;

    private final Rider rider;
    /** Nullable until supplied/recorded; see the operation contract. */
    private final Vehicle vehicle;

    private final String originStationId;
    /** Nullable until supplied/recorded; see the operation contract. */
    private String returnDockId;
    /** Nullable until supplied/recorded; see the operation contract. */
    private Instant startedAt;
    /** Nullable until supplied/recorded; see the operation contract. */
    private Instant endedAt;

    private TripStatus status;

    private PaymentStatus paymentStatus;

    private DepositStatus depositStatus;

    /** Snapshot taken when the trip was created; later configuration changes never reach it. */
    private final PricingConfiguration.FareTerms pricingTerms;
    /** Nullable until supplied/recorded; see the operation contract. */
    private String depositReference;
    /** Nullable until supplied/recorded; see the operation contract. */
    private String paymentReference;

    private final PaymentMethod paymentMethod;

    /** Starting zone's methods, kept so a cross-zone return is settled under the original rules. */
    private final Set<PaymentMethod> allowedMethodsAtStart;
    /** Nullable until supplied/recorded; see the operation contract. */
    private FareBreakdown fare;

    /**
     * Create a trip, usually PENDING, or restore one in a consistent recorded state.
     * @throws IllegalArgumentException if a required value is missing, the payment method is not allowed
     *         at the start, or the timestamps, dock, references or fare do not match the given statuses
     */
    public Trip(UUID id, Rider rider, Vehicle vehicle, String originStationId, String returnDockId, Instant startedAt, Instant endedAt, TripStatus status, PaymentStatus paymentStatus, DepositStatus depositStatus, PricingConfiguration.FareTerms pricingTerms, String depositReference, String paymentReference, PaymentMethod paymentMethod, Set<PaymentMethod> allowedMethodsAtStart, FareBreakdown fare) {
        if (id == null || rider == null || vehicle == null || status == null || paymentStatus == null
                || depositStatus == null || pricingTerms == null || paymentMethod == null) {
            throw new IllegalArgumentException("Trip requires an ID, rider, vehicle, statuses, pricing terms and payment method");
        }
        if (originStationId == null || originStationId.isBlank()) {
            throw new IllegalArgumentException("Origin station ID is required");
        }
        if (allowedMethodsAtStart == null || allowedMethodsAtStart.isEmpty()
                || allowedMethodsAtStart.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Allowed payment methods at start must be a non-empty set without null");
        }
        if (!allowedMethodsAtStart.contains(paymentMethod)) {
            throw new IllegalArgumentException("Payment method " + paymentMethod + " is not allowed in the starting zone");
        }
        validateRecordedState(status, startedAt, endedAt, returnDockId);
        if (status != TripStatus.ENDED && paymentStatus != PaymentStatus.NOT_DUE) {
            throw new IllegalArgumentException("Payment is only due once a trip has ended");
        }
        if (paymentStatus == PaymentStatus.PAID && (isBlank(paymentReference) || fare == null)) {
            throw new IllegalArgumentException("A paid trip requires a payment reference and fare");
        }
        if (depositStatus != DepositStatus.NONE && isBlank(depositReference)) {
            throw new IllegalArgumentException("A deposit hold requires its reference");
        }
        this.id = id;
        this.rider = rider;
        this.vehicle = vehicle;
        this.originStationId = originStationId;
        this.returnDockId = returnDockId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.depositStatus = depositStatus;
        this.pricingTerms = pricingTerms;
        this.depositReference = depositReference;
        this.paymentReference = paymentReference;
        this.paymentMethod = paymentMethod;
        this.allowedMethodsAtStart = new LinkedHashSet<>(allowedMethodsAtStart);
        this.fare = fare;
    }

    /**
     * Require PENDING and confirmed unlock. Set billable start exactly once and become ACTIVE.
     * The caller invokes this only after the docking gateway confirms the unlock.
     * @param unlockedAt time the vehicle was unlocked; billing starts here
     * @throws IllegalArgumentException if the time is missing
     * @throws IllegalStateException if the trip is not PENDING
     */
    public synchronized void beginAt(Instant unlockedAt) {
        if (unlockedAt == null) {
            throw new IllegalArgumentException("Unlock time is required");
        }
        if (status != TripStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING trip can begin; trip " + id + " is " + status);
        }
        startedAt = unlockedAt;
        status = TripStatus.ACTIVE;
    }

    /**
     * Cancel a failed PENDING start without creating billable time. Cancelling again is harmless.
     * @throws IllegalStateException if the trip has already begun
     */
    public synchronized void cancelStart() {
        if (status == TripStatus.CANCELLED) {
            return;
        }
        if (status != TripStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING trip can be cancelled; trip " + id + " is " + status);
        }
        status = TripStatus.CANCELLED;
    }

    /**
     * Record the fare from the trip snapshot; repeated settlement must not change an already paid fare.
     * @param value fare calculated from this trip's pricing terms
     * @throws IllegalArgumentException if the fare is missing
     * @throws IllegalStateException if the trip has not ended, or it is paid and the fare differs
     */
    public synchronized void recordFare(FareBreakdown value) {
        if (value == null) {
            throw new IllegalArgumentException("Fare is required");
        }
        if (status != TripStatus.ENDED) {
            throw new IllegalStateException("A fare can only be recorded for an ENDED trip; trip " + id + " is " + status);
        }
        if (paymentStatus == PaymentStatus.PAID) {
            if (!value.equals(fare)) {
                throw new IllegalStateException("Trip " + id + " is already paid; its fare cannot change");
            }
            return;
        }
        fare = value;
    }

    /**
     * Require lock confirmation before ACTIVE becomes ENDED; capture end/dock once. Financial failure never restarts charges.
     * The caller invokes this only after the docking gateway confirms the lock. Repeating the
     * confirmation for the same dock keeps the original end time.
     * @param confirmedAt time the lock was confirmed; billing stops here
     * @param dockId docking point the vehicle is locked into
     * @throws IllegalArgumentException if a value is missing or the end is before the start
     * @throws IllegalStateException if the trip is not ACTIVE, or already ended at a different dock
     */
    public synchronized void endAt(Instant confirmedAt, String dockId) {
        if (confirmedAt == null || isBlank(dockId)) {
            throw new IllegalArgumentException("Lock confirmation time and dock ID are required");
        }
        if (status == TripStatus.ENDED) {
            if (!dockId.equals(returnDockId)) {
                throw new IllegalStateException("Trip " + id + " already ended at dock " + returnDockId);
            }
            return;
        }
        if (status != TripStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE trip can end; trip " + id + " is " + status);
        }
        if (confirmedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("A trip cannot end before it started");
        }
        endedAt = confirmedAt;
        returnDockId = dockId;
        status = TripStatus.ENDED;
    }

    /**
     * Use fixed end time for ended trips and supplied time for active trips; reject negative elapsed time.
     * Trips that never began have no billable time.
     * @param at evaluation time, used only while the trip is ACTIVE
     * @return elapsed billable time
     * @throws IllegalArgumentException if the time is missing, or before the start of an ACTIVE trip
     */
    public synchronized Duration durationAt(Instant at) {
        if (at == null) {
            throw new IllegalArgumentException("Evaluation time is required");
        }
        return switch (status) {
            case PENDING, CANCELLED -> Duration.ZERO;
            case ENDED -> Duration.between(startedAt, endedAt);
            case ACTIVE -> {
                if (at.isBefore(startedAt)) {
                    throw new IllegalArgumentException("Evaluation time is before the trip started");
                }
                yield Duration.between(startedAt, at);
            }
        };
    }

    /**
     * Record hold/release state and reference; never duplicate a successful hold or release.
     * Deposit changes never touch the trip status or timestamps, so a failed release cannot restart charges.
     * @param status new deposit state
     * @param reference provider reference; required for HELD. For release states a non-null value
     *        records the provider's reference and null keeps the hold reference
     * @throws IllegalArgumentException if the status is missing, a HELD reference is missing, or a reference is blank
     * @throws IllegalStateException if the change would repeat a hold, follow a completed release, or skip the hold
     */
    public synchronized void recordDeposit(DepositStatus status, String reference) {
        if (status == null) {
            throw new IllegalArgumentException("Deposit status is required");
        }
        if (reference != null && reference.isBlank()) {
            throw new IllegalArgumentException("Deposit reference must not be blank");
        }
        switch (status) {
            case NONE -> {
                if (depositStatus != DepositStatus.NONE) {
                    throw new IllegalStateException("Trip " + id + " already has a deposit hold");
                }
                return;
            }
            case HELD -> {
                if (reference == null) {
                    throw new IllegalArgumentException("A deposit hold requires its reference");
                }
                if (depositStatus == DepositStatus.HELD && reference.equals(depositReference)) {
                    return;
                }
                if (depositStatus != DepositStatus.NONE) {
                    throw new IllegalStateException("Trip " + id + " cannot place another deposit hold; deposit is " + depositStatus);
                }
            }
            case RELEASED, RELEASE_PENDING, RELEASE_FAILED -> {
                if (depositStatus == DepositStatus.RELEASED) {
                    if (status == DepositStatus.RELEASED) {
                        return;
                    }
                    throw new IllegalStateException("Trip " + id + " deposit is already released");
                }
                if (depositStatus == DepositStatus.NONE) {
                    throw new IllegalStateException("Trip " + id + " has no deposit hold to release");
                }
            }
        }
        depositStatus = status;
        if (reference != null) {
            depositReference = reference;
        }
    }

    /**
     * Record settlement state/reference without changing ended trip timestamps.
     * A successful payment is final; a failed one may be retried.
     * @param status new payment state
     * @param reference provider reference; required for PAID, optional otherwise
     * @throws IllegalArgumentException if the status is missing, a PAID reference is missing, or a reference is blank
     * @throws IllegalStateException if the trip has not ended, is already paid, or is marked paid without a fare
     */
    public synchronized void recordPayment(PaymentStatus status, String reference) {
        if (status == null) {
            throw new IllegalArgumentException("Payment status is required");
        }
        if (reference != null && reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference must not be blank");
        }
        if (status == PaymentStatus.PAID && reference == null) {
            throw new IllegalArgumentException("A successful payment requires its reference");
        }
        if (paymentStatus == PaymentStatus.PAID) {
            if (status == PaymentStatus.PAID && reference.equals(paymentReference)) {
                return;
            }
            throw new IllegalStateException("Trip " + id + " is already paid");
        }
        if (status == PaymentStatus.NOT_DUE) {
            if (paymentStatus != PaymentStatus.NOT_DUE) {
                throw new IllegalStateException("Payment for trip " + id + " is already due");
            }
            return;
        }
        if (this.status != TripStatus.ENDED) {
            throw new IllegalStateException("Payment can only be recorded for an ENDED trip; trip " + id + " is " + this.status);
        }
        if (status == PaymentStatus.PAID && fare == null) {
            throw new IllegalStateException("Record the fare before marking trip " + id + " paid");
        }
        paymentStatus = status;
        if (reference != null) {
            paymentReference = reference;
        }
    }

    private static void validateRecordedState(TripStatus status, Instant startedAt, Instant endedAt, String returnDockId) {
        boolean started = startedAt != null;
        boolean ended = endedAt != null || returnDockId != null;
        switch (status) {
            case PENDING, CANCELLED -> {
                if (started || ended) {
                    throw new IllegalArgumentException(status + " trips have no start, end or return dock");
                }
            }
            case ACTIVE -> {
                if (!started || ended) {
                    throw new IllegalArgumentException("ACTIVE trips have a start but no end or return dock");
                }
            }
            case ENDED -> {
                if (!started || endedAt == null || isBlank(returnDockId)) {
                    throw new IllegalArgumentException("ENDED trips require a start, end and return dock");
                }
                if (endedAt.isBefore(startedAt)) {
                    throw new IllegalArgumentException("A trip cannot end before it started");
                }
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
    public Rider getRider() {
        return rider;
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
    public String getOriginStationId() {
        return originStationId;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized String getReturnDockId() {
        return returnDockId;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized TripStatus getStatus() {
        return status;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized DepositStatus getDepositStatus() {
        return depositStatus;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public PricingConfiguration.FareTerms getPricingTerms() {
        return pricingTerms;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized String getDepositReference() {
        return depositReference;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized String getPaymentReference() {
        return paymentReference;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Set<PaymentMethod> getAllowedMethodsAtStart() {
        return Set.copyOf(allowedMethodsAtStart);
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized FareBreakdown getFare() {
        return fare;
    }
}
