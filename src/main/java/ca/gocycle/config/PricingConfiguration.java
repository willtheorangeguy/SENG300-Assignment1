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


public class PricingConfiguration {
    private final BigDecimal unlockFee;
    private final Map<String, BigDecimal> rates;
    private final boolean samePricesForAllVehicles;
    private final BigDecimal deposit;
    private final String billingUnit;
    private final String rounding;
    private final BigDecimal minimumTripCharge;
    private final BigDecimal surcharge;
    private final String studentDiscountAppliesTo;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    @JsonCreator
    public PricingConfiguration(@JsonProperty("unlockFee") BigDecimal unlockFee, @JsonProperty("rates") Map<String, BigDecimal> rates, @JsonProperty("samePricesForAllVehicles") boolean samePricesForAllVehicles, @JsonProperty("deposit") BigDecimal deposit, @JsonProperty("billingUnit") String billingUnit, @JsonProperty("rounding") String rounding, @JsonProperty("minimumTripCharge") BigDecimal minimumTripCharge, @JsonProperty("surcharge") BigDecimal surcharge, @JsonProperty("studentDiscountAppliesTo") String studentDiscountAppliesTo) {
        this.unlockFee = unlockFee;
        this.rates = Map.copyOf(rates);
        this.samePricesForAllVehicles = samePricesForAllVehicles;
        this.deposit = deposit;
        this.billingUnit = billingUnit;
        this.rounding = rounding;
        this.minimumTripCharge = minimumTripCharge;
        this.surcharge = surcharge;
        this.studentDiscountAppliesTo = studentDiscountAppliesTo;
    }

    /**
     * Create immutable monetary terms from the selected category/type; preserve BigDecimal precision.
     * One unlock fee, deposit and surcharge apply to every vehicle; only the per-minute rate depends on
     * the rider category. Because the values are copied into a record, later configuration changes never
     * alter a quote that a trip has already taken.
     * @param category rider category key in {@code rates}, for example {@code "CASUAL"}
     * @param type vehicle being quoted; prices are shared, so it only has to be present
     * @return the terms to snapshot onto a trip
     * @throws IllegalArgumentException if the category is blank or the vehicle type is missing
     * @throws NoSuchElementException if no rate is configured for the category
     * @throws IllegalStateException if the configuration asks for per-vehicle prices, which are not supported
     */
    public FareTerms snapshot(String category, VehicleType type) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Pricing category is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type is required");
        }
        if (!samePricesForAllVehicles) {
            throw new IllegalStateException("Per-vehicle pricing is not supported; samePricesForAllVehicles must be true");
        }
        BigDecimal perMinute = rates.get(category);
        if (perMinute == null) {
            throw new NoSuchElementException("No per-minute rate is configured for category " + category);
        }
        return new FareTerms(category, unlockFee, perMinute, deposit, surcharge, billingUnit, rounding, minimumTripCharge);
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("unlockFee")
    public BigDecimal getUnlockFee() {
        return unlockFee;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("rates")
    public Map<String, BigDecimal> getRates() {
        return Map.copyOf(rates);
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("samePricesForAllVehicles")
    public boolean getSamePricesForAllVehicles() {
        return samePricesForAllVehicles;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("deposit")
    public BigDecimal getDeposit() {
        return deposit;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("billingUnit")
    public String getBillingUnit() {
        return billingUnit;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("rounding")
    public String getRounding() {
        return rounding;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("minimumTripCharge")
    public BigDecimal getMinimumTripCharge() {
        return minimumTripCharge;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("surcharge")
    public BigDecimal getSurcharge() {
        return surcharge;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    @JsonProperty("studentDiscountAppliesTo")
    public String getStudentDiscountAppliesTo() {
        return studentDiscountAppliesTo;
    }

    /** Immutable typed settings; JSON property names match record components. */
    public record FareTerms(String category, BigDecimal unlockFee, BigDecimal perMinute, BigDecimal deposit, BigDecimal surcharge, String billingUnit, String rounding, BigDecimal minimumTripCharge) {}
}
