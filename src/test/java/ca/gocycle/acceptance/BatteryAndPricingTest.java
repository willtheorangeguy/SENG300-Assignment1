package ca.gocycle.acceptance;

import ca.gocycle.config.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.pricing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ca.gocycle.acceptance.Fixtures.*;

class BatteryAndPricingTest {
    @ParameterizedTest @CsvSource({"19,false","20,true","21,true"})
    void batteryBoundary(int percent,boolean rentable) { assertEquals(rentable,electric(percent,NOW).isRentable(NOW)); }
    @Test void unknownAndStaleBatteryReject() {
        assertFalse(electric(null,null).isRentable(NOW));assertFalse(electric(80,NOW.minusSeconds(601)).isRentable(NOW));
    }
    @Test void outOfServiceAndRentedVehicleReject() {
        var v=electric(20,NOW);v.setServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE);assertFalse(v.isRentable(NOW));
        var rented=electric(80,Instant.now());rented.reserve();rented.markRented();assertFalse(rented.isRentable(NOW));
    }
    @ParameterizedTest @Tag("pending-implementation") @CsvSource({"0,0,0.00,5.00","1,1,0.00,5.50","60,1,0.00,5.50","61,2,0.00,6.00","7200,120,0.00,65.00","7201,121,15.00,80.50"})
    void fareRoundingAndActualDurationSurcharge(long seconds,long minutes,String surcharge,String total) throws Exception {
        var fare=pricing(config()).calculateFare(ended(seconds));
        assertEquals(minutes,fare.billableMinutes());assertEquals(new BigDecimal("5.00"),fare.unlockCharge());
        assertEquals(new BigDecimal(surcharge),fare.surcharge());assertEquals(new BigDecimal(total),fare.total());
        assertEquals(new BigDecimal("0.50").multiply(BigDecimal.valueOf(minutes)),fare.durationCharge());
    }
    @Test @Tag("pending-implementation") void configuredCategoryRates() throws Exception {
        var c=config();var p=pricing(c);
        assertEquals(new BigDecimal("0.50"),p.quoteTerms(rider(),VehicleType.PEDAL_BIKE,NOW).perMinute());
        var member=new Membership(NOW.minusSeconds(1),NOW.plusSeconds(30*86400),"paid");
        assertEquals(new BigDecimal("0.20"),p.quoteTerms(rider(null,null,member),VehicleType.ELECTRIC_BIKE,NOW).perMinute());
        assertEquals(new BigDecimal("0.20"),p.quoteTerms(rider("UCALGARY",NOW.plusSeconds(86400),null),VehicleType.ELECTRIC_SCOOTER,NOW).perMinute());
    }
    @Test @Tag("pending-implementation") void registeredPolicyCanExtendPricing() throws Exception {
        PricingPolicy added=new PricingPolicy() {
            public boolean supports(ca.gocycle.domain.rider.Rider r,Instant at,DeploymentConfiguration c){return true;}
            public PricingConfiguration.FareTerms termsFor(VehicleType t,PricingConfiguration c){return terms("ADDED");}
        };
        var p=new PricingService(List.of(added,new CasualPricingPolicy()),config());
        assertEquals("ADDED",p.quoteTerms(rider(),VehicleType.PEDAL_BIKE,NOW).category());
    }
    @Test @Tag("pending-implementation") void snapshotCopiesEveryMonetaryTerm() throws Exception {
        var c=config().getPricing();Map<String,BigDecimal> rates=new HashMap<>(c.getRates());
        var mutable=new PricingConfiguration(c.getUnlockFee(),rates,c.getSamePricesForAllVehicles(),c.getDeposit(),c.getBillingUnit(),c.getRounding(),c.getMinimumTripCharge(),c.getSurcharge(),c.getStudentDiscountAppliesTo());
        var quote=mutable.snapshot("CASUAL",VehicleType.PEDAL_BIKE);
        rates.put("CASUAL",new BigDecimal("99.00"));
        assertEquals(new BigDecimal("5.00"),quote.unlockFee());assertEquals(new BigDecimal("0.50"),quote.perMinute());
        assertEquals(new BigDecimal("50.00"),quote.deposit());assertEquals(new BigDecimal("15.00"),quote.surcharge());
    }
}
