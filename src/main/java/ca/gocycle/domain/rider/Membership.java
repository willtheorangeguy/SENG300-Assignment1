package ca.gocycle.domain.rider;

import java.time.Instant;

/** A paid membership interval; price and duration are supplied by the registration service. */
public class Membership {
    private final Instant validFrom;
    private final Instant validUntil;
    private final String paymentReference;

    /**
     * The interval is start-inclusive and end-exclusive. Null payment means not yet paid.
     * @throws IllegalArgumentException for missing/reversed dates or a blank payment reference
     */
    public Membership(Instant validFrom, Instant validUntil, String paymentReference) {
        if (validFrom == null || validUntil == null || !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("Membership end must be after its start");
        }
        if (paymentReference != null && paymentReference.isBlank()) {
            throw new IllegalArgumentException("Payment reference must not be blank");
        }
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.paymentReference = paymentReference;
    }

    /** @throws IllegalArgumentException if the evaluation time is null */
    public boolean isActiveAt(Instant at) {
        if (at == null) throw new IllegalArgumentException("Evaluation time is required");
        return paymentReference != null && !at.isBefore(validFrom) && at.isBefore(validUntil);
    }

    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public String getPaymentReference() { return paymentReference; }
}
