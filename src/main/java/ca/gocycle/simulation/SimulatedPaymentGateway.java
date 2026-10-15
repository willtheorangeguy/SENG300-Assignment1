package ca.gocycle.simulation;

import ca.gocycle.data.PaymentResult;
import ca.gocycle.domain.PaymentMethod;
import ca.gocycle.integration.PaymentGateway;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A pretend payment provider for development and tests.
 *
 * <p>The three constructor flags decide whether every hold, charge or release succeeds or is
 * declined. Each result is remembered under its {@code requestId}; asking again with the same
 * {@code requestId} returns the remembered result without doing the operation a second time
 * (this is how a real provider avoids charging twice). A retry after a failure must therefore
 * use a new {@code requestId}.
 *
 * <p>Because results are kept in one map, a {@code requestId} should not be reused for a
 * different operation.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private final boolean holdOutcome;
    private final boolean chargeOutcome;
    private final boolean releaseOutcome;
    private final Map<String, PaymentResult> resultsByRequestId;

    /**
     * @param holdOutcome    true if every hold authorization succeeds
     * @param chargeOutcome  true if every charge succeeds
     * @param releaseOutcome true if every hold release succeeds
     */
    public SimulatedPaymentGateway(boolean holdOutcome, boolean chargeOutcome, boolean releaseOutcome) {
        this.holdOutcome = holdOutcome;
        this.chargeOutcome = chargeOutcome;
        this.releaseOutcome = releaseOutcome;
        this.resultsByRequestId = new LinkedHashMap<>();
    }

    /**
     * Authorize the quoted amount with the selected method/currency and stable request ID.
     *
     * @return the stored result for this request ID, or a new success/failure result
     * @throws IllegalArgumentException if the amount is null or not positive, or the currency or request ID is
     *                                  blank, or the method is null
     */
    @Override
    public synchronized PaymentResult authorizeHold(BigDecimal amount, String currency, PaymentMethod method, String requestId) {
        requirePayment(amount, currency, method, requestId);
        return resultFor(requestId, holdOutcome, "hold-", "Simulated hold was declined");
    }

    /**
     * Charge once per stable logical request; an explicit new retry may use a new attempt key.
     *
     * @return the stored result for this request ID, or a new success/failure result
     * @throws IllegalArgumentException if the amount is null or not positive, or the currency or request ID is
     *                                  blank, or the method is null
     */
    @Override
    public synchronized PaymentResult charge(BigDecimal amount, String currency, PaymentMethod method, String requestId) {
        requirePayment(amount, currency, method, requestId);
        return resultFor(requestId, chargeOutcome, "charge-", "Simulated charge was declined");
    }

    /**
     * Release the identified hold once per logical request.
     *
     * @return the stored result for this request ID, or a new success/failure result
     * @throws IllegalArgumentException if the hold reference or request ID is blank
     */
    @Override
    public synchronized PaymentResult releaseHold(String reference, String requestId) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        requireRequestId(requestId);
        return resultFor(requestId, releaseOutcome, "release-", "Simulated release was declined");
    }

    public boolean getHoldOutcome() {
        return holdOutcome;
    }

    public boolean getChargeOutcome() {
        return chargeOutcome;
    }

    public boolean getReleaseOutcome() {
        return releaseOutcome;
    }

    /** @return an immutable snapshot of every result handed out so far, keyed by request ID */
    public synchronized Map<String, PaymentResult> getResultsByRequestId() {
        return Map.copyOf(resultsByRequestId);
    }

    private PaymentResult resultFor(String requestId, boolean outcome, String referencePrefix, String failureMessage) {
        PaymentResult existing = resultsByRequestId.get(requestId);
        if (existing != null) {
            return existing;
        }
        PaymentResult created = outcome
                ? new PaymentResult(true, referencePrefix + requestId, null, null)
                : new PaymentResult(false, null, "DECLINED", failureMessage);
        resultsByRequestId.put(requestId, created);
        return created;
    }

    private static void requirePayment(BigDecimal amount, String currency, PaymentMethod method, String requestId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        requireRequestId(requestId);
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }
}
