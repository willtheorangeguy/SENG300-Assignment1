package ca.gocycle.application;

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

import java.math.*;
import java.time.*;
import java.util.*;

public class PaymentService {
    private final PaymentGateway gateway;
    private final ReceiptDelivery receiptDelivery;
    private final DeploymentConfiguration configuration;
    private final Clock clock;
    private final Map<String, Integer> attemptsByOperation;
    private final Map<UUID, Receipt> receiptsByTrip;
    private final Set<UUID> deliveredReceipts = new HashSet<>();

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public PaymentService(
            PaymentGateway gateway,
            ReceiptDelivery receiptDelivery,
            DeploymentConfiguration configuration,
            Clock clock) {
        this.gateway = gateway;
        this.receiptDelivery = receiptDelivery;
        this.configuration = configuration;
        this.clock = clock;
        this.attemptsByOperation = new LinkedHashMap<>();
        this.receiptsByTrip = new LinkedHashMap<>();
    }

    /**
     * Validate zone permissions and stable request ID; provider failures return PaymentResult
     * without activating membership.
     *
     * @param rider required input; validate malformed values before mutation
     * @param method required input; validate malformed values before mutation
     * @param zone required input; validate malformed values before mutation
     * @param requestId required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized PaymentResult payMembership(
            Rider rider, PaymentMethod method, Zone zone, String requestId) {

        if (rider == null
                || method == null
                || zone == null
                || requestId == null
                || requestId.isBlank()) {
            throw new IllegalArgumentException("Invalid membership payment");
        }

        if (!zone.getAllowedPaymentMethods().contains(method)) {
            throw new IllegalStateException("Payment method not allowed in this zone");
        }

        BigDecimal price = configuration.getMembership().price();

        return gateway.charge(price, "CAD", method, requestId);
    }

    /**
     * Validate starting-zone methods before provider call; use a stable key per logical attempt and
     * retain successful results.
     *
     * @param trip required input; validate malformed values before mutation
     * @param zone required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized PaymentResult holdDeposit(Trip trip, Zone zone) {

        if (trip == null || zone == null) {
            throw new IllegalArgumentException("Trip and zone cannot be null");
        }

        PaymentMethod method = trip.getPaymentMethod();

        if (method == null) {
            throw new IllegalArgumentException("Payment method cannot be null");
        }

        if (!trip.getAllowedMethodsAtStart().contains(method)
                || !zone.getAllowedPaymentMethods().contains(method)) {
            throw new IllegalStateException("Payment method not allowed");
        }

        BigDecimal deposit = configuration.getPricing().getDeposit();

        String requestId = "hold-" + trip.getId();

        return gateway.authorizeHold(deposit, "CAD", method, requestId);
    }

    /**
     * Release at confirmed trip end independent of fare collection; retry failures within limits
     * without repeating success.
     *
     * @param trip required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized PaymentResult releaseDeposit(Trip trip) {

        if (trip == null) {
            throw new IllegalArgumentException("Trip cannot be null");
        }

        if (trip.getStatus() != TripStatus.ENDED && trip.getStatus() != TripStatus.CANCELLED) {
            throw new IllegalStateException("Trip has not ended");
        }

        if (trip.getDepositStatus() == DepositStatus.RELEASED) {
            return new PaymentResult(true, trip.getDepositReference(), null, null);
        }

        if (trip.getDepositStatus() != DepositStatus.HELD
                && trip.getDepositStatus() != DepositStatus.RELEASE_FAILED) {
            throw new IllegalStateException("No deposit available to release");
        }

        String reference = trip.getDepositReference();

        if (reference == null || reference.isBlank()) {
            throw new IllegalStateException("Deposit reference is missing");
        }

        String operation = "release-" + trip.getId();
        int attempt = attemptsByOperation.getOrDefault(operation, 1);
        if (trip.getDepositStatus() == DepositStatus.RELEASE_FAILED) {
            attempt++;
        }
        String requestId = attempt == 1 ? operation : operation + "-attempt-" + attempt;
        // Advance only after a definitive response. An exception can have an unknown
        // provider outcome, so retry that same idempotency key.
        PaymentResult result = gateway.releaseHold(reference, requestId);
        attemptsByOperation.put(operation, attempt);

        if (result.success()) {
            trip.recordDeposit(DepositStatus.RELEASED, reference);
        } else {
            trip.recordDeposit(DepositStatus.RELEASE_FAILED, reference);
        }

        return result;
    }

    /**
     * Require ended trip and immutable fare; keep ended/unpaid on failure, with stable keys and
     * bounded explicit retries.
     *
     * @param trip required input; validate malformed values before mutation
     * @param fare nullable only where documented
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized PaymentResult chargeFare(Trip trip, FareBreakdown fare) {

        if (trip == null || fare == null) {
            throw new IllegalArgumentException("Trip and fare cannot be null");
        }

        if (trip.getStatus() != TripStatus.ENDED) {
            throw new IllegalStateException("Trip has not ended");
        }

        if (trip.getPaymentStatus() == PaymentStatus.PAID) {
            if (!fare.equals(trip.getFare())) {
                throw new IllegalStateException("Fare cannot change after payment");
            }

            return new PaymentResult(true, trip.getPaymentReference(), null, null);
        }

        BigDecimal amount = fare.total();

        PaymentMethod method = trip.getPaymentMethod();

        if (method == null) {
            throw new IllegalStateException("Payment method is missing");
        }

        trip.recordFare(fare);

        String requestId = "fare-" + trip.getId();

        int attempt = attemptsByOperation.getOrDefault(requestId, 1);
        if (trip.getPaymentStatus() == PaymentStatus.FAILED) {
            attempt++;
        }
        String operation = requestId;
        if (attempt > 1) {
            requestId = requestId + "-attempt-" + attempt;
        }

        PaymentResult result = gateway.charge(amount, "CAD", method, requestId);
        attemptsByOperation.put(operation, attempt);

        if (result.success()) {
            trip.recordPayment(PaymentStatus.PAID, result.reference());
        } else {
            trip.recordPayment(PaymentStatus.FAILED, null);
        }

        return result;
    }

    /**
     * Require ended and paid trip; deliver at most one receipt per successful logical request,
     * retaining failures for manual retry.
     *
     * @param trip required input; validate malformed values before mutation
     * @param channel required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized boolean sendReceipt(Trip trip, ReceiptChannel channel) {

        if (trip == null || channel == null) {
            throw new IllegalArgumentException("Trip and channel cannot be null");
        }

        if (trip.getStatus() != TripStatus.ENDED) {
            throw new IllegalStateException("Trip has not ended");
        }

        if (trip.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Trip has not been paid");
        }

        Receipt receipt = receiptsByTrip.get(trip.getId());

        if (receipt == null
                || (!deliveredReceipts.contains(trip.getId())
                        && receipt.depositStatus() != trip.getDepositStatus())) {
            receipt =
                    new Receipt(
                            UUID.randomUUID(),
                            trip.getId(),
                            clock.instant(),
                            "CAD",
                            trip.getFare(),
                            trip.getDepositStatus());

            receiptsByTrip.put(trip.getId(), receipt);
        }

        if (deliveredReceipts.contains(trip.getId())) {
            return true;
        }

        boolean delivered = receiptDelivery.deliver(receipt, channel, trip.getRider());

        if (delivered) {
            deliveredReceipts.add(trip.getId());
        }

        return delivered;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public PaymentGateway getGateway() {
        return gateway;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public ReceiptDelivery getReceiptDelivery() {
        return receiptDelivery;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public DeploymentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public Clock getClock() {
        return clock;
    }
}
