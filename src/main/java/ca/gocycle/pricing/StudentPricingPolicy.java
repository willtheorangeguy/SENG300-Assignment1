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


/** Strategy for riders with current student evidence from the configured institution. */
public class StudentPricingPolicy implements PricingPolicy {

    /** Rate key in {@code pricing.rates}. */
    private static final String CATEGORY = "STUDENT";

    /** The only supported scope of the student discount. */
    private static final String PER_MINUTE_ONLY = "PER_MINUTE_ONLY";

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public StudentPricingPolicy() {
    }

    /**
     * Support riders whose student evidence is still valid at the supplied time and whose email
     * belongs to the configured student domain.
     * @param rider rider starting the trip
     * @param at evaluation time
     * @param config deployment configuration
     * @return whether student pricing applies
     * @throws IllegalArgumentException if any input is missing
     * @throws IllegalStateException if the student rules are missing or the rider is also an active member
     */
    public boolean supports(Rider rider, Instant at, DeploymentConfiguration config) {
        if (rider == null || at == null || config == null) {
            throw new IllegalArgumentException("Rider, evaluation time and configuration are required");
        }
        if (config.getStudents() == null || config.getStudents().emailDomain() == null) {
            throw new IllegalStateException("Student rules are not configured");
        }
        if (!rider.hasValidStudentEvidence(at)) {
            return false;
        }
        String domain = "@" + config.getStudents().emailDomain().toLowerCase(Locale.ROOT);
        if (!rider.getEmail().toLowerCase(Locale.ROOT).endsWith(domain)) {
            return false;
        }
        if (rider.hasActiveMembership(at)) {
            throw new IllegalStateException("A rider cannot hold student and member pricing at the same time");
        }
        return true;
    }

    /**
     * Return the configured STUDENT terms. The student discount only lowers the per-minute rate, so
     * every other amount comes from the shared configuration.
     * @param type vehicle being quoted
     * @param config pricing configuration to snapshot
     * @return the STUDENT fare terms
     * @throws IllegalArgumentException if any input is missing
     * @throws IllegalStateException if the discount is configured to apply to anything but the per-minute rate
     * @throws NoSuchElementException if no STUDENT rate is configured
     */
    public PricingConfiguration.FareTerms termsFor(VehicleType type, PricingConfiguration config) {
        if (type == null || config == null) {
            throw new IllegalArgumentException("Vehicle type and pricing configuration are required");
        }
        if (!PER_MINUTE_ONLY.equals(config.getStudentDiscountAppliesTo())) {
            throw new IllegalStateException("Student discount must apply to the per-minute rate only");
        }
        return config.snapshot(CATEGORY, type);
    }
}
