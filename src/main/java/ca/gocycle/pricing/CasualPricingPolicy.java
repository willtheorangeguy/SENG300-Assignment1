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


/** Strategy for riders without a discount. It accepts everyone, so it must be registered last. */
public class CasualPricingPolicy implements PricingPolicy {

    /** Rate key in {@code pricing.rates}. */
    private static final String CATEGORY = "CASUAL";

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public CasualPricingPolicy() {
    }

    /**
     * Casual pricing is the fallback category, so it supports every rider. Register it after the
     * discounted policies so they get the first chance to claim the rider.
     * @param rider rider starting the trip
     * @param at evaluation time
     * @param config deployment configuration
     * @return always {@code true} for valid input
     * @throws IllegalArgumentException if any input is missing
     */
    public boolean supports(Rider rider, Instant at, DeploymentConfiguration config) {
        if (rider == null || at == null || config == null) {
            throw new IllegalArgumentException("Rider, evaluation time and configuration are required");
        }
        return true;
    }

    /**
     * Return the configured CASUAL terms; missing terms are configuration errors, with no hard-coded fallback.
     * @param type vehicle being quoted
     * @param config pricing configuration to snapshot
     * @return the CASUAL fare terms
     * @throws IllegalArgumentException if any input is missing
     * @throws NoSuchElementException if no CASUAL rate is configured
     */
    public PricingConfiguration.FareTerms termsFor(VehicleType type, PricingConfiguration config) {
        if (type == null || config == null) {
            throw new IllegalArgumentException("Vehicle type and pricing configuration are required");
        }
        return config.snapshot(CATEGORY, type);
    }
}
