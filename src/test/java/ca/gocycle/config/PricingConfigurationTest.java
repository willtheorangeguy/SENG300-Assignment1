package ca.gocycle.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.gocycle.domain.VehicleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class PricingConfigurationTest {

    private static PricingConfiguration example() throws Exception {
        return new ObjectMapper().readValue(Path.of("config/gocycle.example.json").toFile(), DeploymentConfiguration.class)
                .getPricing();
    }

    private static PricingConfiguration withSamePrices(PricingConfiguration c, boolean same) {
        return new PricingConfiguration(c.getUnlockFee(), c.getRates(), same, c.getDeposit(), c.getBillingUnit(),
                c.getRounding(), c.getMinimumTripCharge(), c.getSurcharge(), c.getStudentDiscountAppliesTo());
    }

    @Test
    void snapshotCopiesConfiguredTermsForTheCategory() throws Exception {
        var terms = example().snapshot("CASUAL", VehicleType.PEDAL_BIKE);
        assertEquals("CASUAL", terms.category());
        assertEquals(new BigDecimal("5.00"), terms.unlockFee());
        assertEquals(new BigDecimal("0.50"), terms.perMinute());
        assertEquals(new BigDecimal("50.00"), terms.deposit());
        assertEquals(new BigDecimal("15.00"), terms.surcharge());
        assertEquals("MINUTE", terms.billingUnit());
        assertEquals("UP", terms.rounding());
        assertNull(terms.minimumTripCharge());
    }

    @Test
    void reducedCategoriesChangeOnlyThePerMinuteRate() throws Exception {
        var c = example();
        var casual = c.snapshot("CASUAL", VehicleType.ELECTRIC_BIKE);
        for (String category : new String[] {"MEMBER", "STUDENT"}) {
            var reduced = c.snapshot(category, VehicleType.ELECTRIC_BIKE);
            assertEquals(new BigDecimal("0.20"), reduced.perMinute());
            assertEquals(casual.unlockFee(), reduced.unlockFee());
            assertEquals(casual.deposit(), reduced.deposit());
            assertEquals(casual.surcharge(), reduced.surcharge());
        }
    }

    @Test
    void everyVehicleTypeSharesTheSamePrices() throws Exception {
        var c = example();
        var pedal = c.snapshot("MEMBER", VehicleType.PEDAL_BIKE);
        for (VehicleType type : VehicleType.values()) {
            assertEquals(pedal, c.snapshot("MEMBER", type));
        }
    }

    @Test
    void laterRateChangesDoNotAlterAnEarlierSnapshot() throws Exception {
        var c = example();
        Map<String, BigDecimal> rates = new HashMap<>(c.getRates());
        var mutable = new PricingConfiguration(c.getUnlockFee(), rates, true, c.getDeposit(), c.getBillingUnit(),
                c.getRounding(), c.getMinimumTripCharge(), c.getSurcharge(), c.getStudentDiscountAppliesTo());
        var quote = mutable.snapshot("CASUAL", VehicleType.PEDAL_BIKE);
        rates.put("CASUAL", new BigDecimal("99.00"));
        assertEquals(new BigDecimal("0.50"), quote.perMinute());
        assertEquals(new BigDecimal("0.50"), mutable.snapshot("CASUAL", VehicleType.PEDAL_BIKE).perMinute());
    }

    @Test
    void malformedOrUnknownInputsAreRejected() throws Exception {
        var c = example();
        assertThrows(IllegalArgumentException.class, () -> c.snapshot(null, VehicleType.PEDAL_BIKE));
        assertThrows(IllegalArgumentException.class, () -> c.snapshot(" ", VehicleType.PEDAL_BIKE));
        assertThrows(IllegalArgumentException.class, () -> c.snapshot("CASUAL", null));
        assertThrows(NoSuchElementException.class, () -> c.snapshot("SENIOR", VehicleType.PEDAL_BIKE));
    }

    @Test
    void perVehiclePricingIsNotSupported() throws Exception {
        var perVehicle = withSamePrices(example(), false);
        assertThrows(IllegalStateException.class, () -> perVehicle.snapshot("CASUAL", VehicleType.PEDAL_BIKE));
    }
}
