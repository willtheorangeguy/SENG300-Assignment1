package ca.gocycle.pricing;

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
import ca.gocycle.presentation.*;
import ca.gocycle.simulation.*;


public class PricingService {

    private final List<PricingPolicy> policies;

    private final DeploymentConfiguration configuration;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public PricingService(List<PricingPolicy> policies, DeploymentConfiguration configuration) {
        this.policies = new ArrayList<>(policies);
        this.configuration = configuration;
    }

    /**
     * Choose the first supporting registered policy in configured order. Keep TripService independent of category switches.
     * @param rider required input; validate malformed values before mutation
     * @param type required input; validate malformed values before mutation
     * @param at required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public PricingConfiguration.FareTerms quoteTerms(Rider rider, VehicleType type, Instant at) {

    if (rider == null || type == null || at == null) {
        throw new IllegalArgumentException("Invalid pricing inputs");
    }

    for (PricingPolicy policy : policies) {
    if (policy.supports(rider, at, configuration)) {
        return policy.termsFor(type, configuration.getPricing());
    }
}
throw new IllegalStateException("No matching pricing policy found");
}

    /**
     * Use snapshotted fees and started-minute rounding. Apply one surcharge only when actual elapsed duration exceeds 120 minutes.
     * @param trip required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public FareBreakdown calculateFare(Trip trip) {

    if (trip == null) {
        throw new IllegalArgumentException("Trip cannot be null");
    }

    PricingConfiguration.FareTerms terms = trip.getPricingTerms();

if (terms == null) {
    throw new IllegalStateException("Trip has no pricing terms");
}
Instant start = trip.getStartedAt();
Instant end = trip.getEndedAt();

if (start == null || end == null) {
    throw new IllegalStateException("Trip must have a start and end time");
}

if (end.isBefore(start)) {
    throw new IllegalStateException("End time cannot be before start time");
}
long seconds = Duration.between(start, end).getSeconds();

long minutes = (seconds + 59) / 60;

BigDecimal unlockCharge = terms.unlockFee();

BigDecimal durationCharge = terms.perMinute()
        .multiply(BigDecimal.valueOf(minutes));

        BigDecimal surcharge = BigDecimal.ZERO;

if (seconds > 120 * 60) {
    surcharge = terms.surcharge();
}

BigDecimal total = unlockCharge.add(durationCharge).add(surcharge);

if (total.compareTo(terms.minimumTripCharge()) < 0) {
    total = terms.minimumTripCharge();
}

return new FareBreakdown(
    unlockCharge, minutes, durationCharge, surcharge, total
);

}

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public List<PricingPolicy> getPolicies() {
        return List.copyOf(policies);
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public DeploymentConfiguration getConfiguration() {
        return configuration;
    }
}
