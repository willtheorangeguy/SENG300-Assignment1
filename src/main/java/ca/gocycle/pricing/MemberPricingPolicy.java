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


/** Strategy for riders holding a paid monthly membership. */
public class MemberPricingPolicy implements PricingPolicy {

    /** Rate key in {@code pricing.rates}. */
    private static final String CATEGORY = "MEMBER";

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public MemberPricingPolicy() {
    }

    /**
     * Support riders with a paid monthly membership that is active at the supplied time.
     * @param rider rider starting the trip
     * @param at evaluation time
     * @param config deployment configuration
     * @return whether member pricing applies
     * @throws IllegalArgumentException if any input is missing
     * @throws IllegalStateException if the rider is both an active member and a verified student
     */
    public boolean supports(Rider rider, Instant at, DeploymentConfiguration config) {
        if (rider == null || at == null || config == null) {
            throw new IllegalArgumentException("Rider, evaluation time and configuration are required");
        }
        if (!rider.hasActiveMembership(at)) {
            return false;
        }
        if (rider.hasValidStudentEvidence(at)) {
            throw new IllegalStateException("A rider cannot hold student and member pricing at the same time");
        }
        return true;
    }

    /**
     * Return the configured MEMBER terms; missing terms are configuration errors, with no hard-coded fallback.
     * @param type vehicle being quoted
     * @param config pricing configuration to snapshot
     * @return the MEMBER fare terms
     * @throws IllegalArgumentException if any input is missing
     * @throws NoSuchElementException if no MEMBER rate is configured
     */
    public PricingConfiguration.FareTerms termsFor(VehicleType type, PricingConfiguration config) {
        if (type == null || config == null) {
            throw new IllegalArgumentException("Vehicle type and pricing configuration are required");
        }
        return config.snapshot(CATEGORY, type);
    }
}
