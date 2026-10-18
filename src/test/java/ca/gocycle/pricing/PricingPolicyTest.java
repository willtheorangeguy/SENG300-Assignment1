package ca.gocycle.pricing;

import ca.gocycle.config.*;
import ca.gocycle.data.InterfacePreferences;
import ca.gocycle.domain.VehicleType;
import ca.gocycle.domain.rider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PricingPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private DeploymentConfiguration config() throws Exception {
        return new ObjectMapper().readValue(Path.of("config/gocycle.example.json").toFile(), DeploymentConfiguration.class);
    }
    private Rider rider(String email, String institution, Instant until, Membership membership) {
        return new Rider(UUID.randomUUID(), "Test Rider", email, LocalDate.of(2000, 1, 1), institution, until,
                membership, new InterfacePreferences("en", false));
    }
    private Rider casual() {
        return rider("rider@ucalgary.ca", null, null, null);
    }
    private Rider student(Instant until) {
        return rider("student@ucalgary.ca", "UCALGARY", until, null);
    }
    private Rider member(String paymentReference) {
        return rider("member@example.com", null, null,
                new Membership(NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(29)), paymentReference));
    }
    private PricingConfiguration withStudentScope(PricingConfiguration c, String scope) {
        return new PricingConfiguration(c.getUnlockFee(), c.getRates(), c.getSamePricesForAllVehicles(), c.getDeposit(),
                c.getBillingUnit(), c.getRounding(), c.getMinimumTripCharge(), c.getSurcharge(), scope);
    }

    @Test void casualIsTheFallbackForEveryRider() throws Exception {
        var c = config();
        var casual = new CasualPricingPolicy();
        assertTrue(casual.supports(casual(), NOW, c));
        assertTrue(casual.supports(student(NOW.plusSeconds(60)), NOW, c));
        assertTrue(casual.supports(member("paid"), NOW, c));
        var terms = casual.termsFor(VehicleType.PEDAL_BIKE, c.getPricing());
        assertEquals("CASUAL", terms.category());
        assertEquals(new BigDecimal("0.50"), terms.perMinute());
    }

    @Test void memberPricingRequiresAPaidActiveMembership() throws Exception {
        var c = config();
        var policy = new MemberPricingPolicy();
        assertTrue(policy.supports(member("paid"), NOW, c));
        assertFalse(policy.supports(member(null), NOW, c));
        assertFalse(policy.supports(member("paid"), NOW.plus(Duration.ofDays(29)), c));
        assertFalse(policy.supports(casual(), NOW, c));
        var terms = policy.termsFor(VehicleType.ELECTRIC_SCOOTER, c.getPricing());
        assertEquals("MEMBER", terms.category());
        assertEquals(new BigDecimal("0.20"), terms.perMinute());
    }

    @Test void studentPricingRequiresCurrentEvidenceAndTheConfiguredDomain() throws Exception {
        var c = config();
        var policy = new StudentPricingPolicy();
        assertTrue(policy.supports(student(NOW.plusSeconds(1)), NOW, c));
        // Evidence expires exclusively at its recorded instant.
        assertFalse(policy.supports(student(NOW), NOW, c));
        assertFalse(policy.supports(rider("student@gmail.com", "UCALGARY", NOW.plusSeconds(60), null), NOW, c));
        assertTrue(policy.supports(rider("Student@UCalgary.CA", "UCALGARY", NOW.plusSeconds(60), null), NOW, c));
        assertFalse(policy.supports(casual(), NOW, c));
        var terms = policy.termsFor(VehicleType.ELECTRIC_BIKE, c.getPricing());
        assertEquals("STUDENT", terms.category());
        assertEquals(new BigDecimal("0.20"), terms.perMinute());
        assertEquals(c.getPricing().getUnlockFee(), terms.unlockFee());
    }

    @Test void studentDiscountOnlyAppliesToThePerMinuteRate() throws Exception {
        var pricing = withStudentScope(config().getPricing(), "WHOLE_FARE");
        assertThrows(IllegalStateException.class, () -> new StudentPricingPolicy().termsFor(VehicleType.PEDAL_BIKE, pricing));
    }

    @Test void expiredStudentWithLaterMembershipIsPricedAsMember() throws Exception {
        var c = config();
        var r = rider("student@ucalgary.ca", "UCALGARY", NOW.minusSeconds(60),
                new Membership(NOW.minusSeconds(30), NOW.plus(Duration.ofDays(30)), "paid"));
        assertFalse(new StudentPricingPolicy().supports(r, NOW, c));
        assertTrue(new MemberPricingPolicy().supports(r, NOW, c));
    }

    @Test void missingInputsAreRejected() throws Exception {
        var c = config();
        for (PricingPolicy p : List.of(new StudentPricingPolicy(), new MemberPricingPolicy(), new CasualPricingPolicy())) {
            assertThrows(IllegalArgumentException.class, () -> p.supports(null, NOW, c));
            assertThrows(IllegalArgumentException.class, () -> p.supports(casual(), null, c));
            assertThrows(IllegalArgumentException.class, () -> p.supports(casual(), NOW, null));
            assertThrows(IllegalArgumentException.class, () -> p.termsFor(null, c.getPricing()));
            assertThrows(IllegalArgumentException.class, () -> p.termsFor(VehicleType.PEDAL_BIKE, null));
        }
    }

    @Test void missingCategoryRateIsAConfigurationErrorNotAFallback() throws Exception {
        var c = config().getPricing();
        var rates = new HashMap<>(c.getRates());
        rates.remove("MEMBER");
        var incomplete = new PricingConfiguration(c.getUnlockFee(), rates, true, c.getDeposit(), c.getBillingUnit(),
                c.getRounding(), c.getMinimumTripCharge(), c.getSurcharge(), c.getStudentDiscountAppliesTo());
        assertThrows(NoSuchElementException.class, () -> new MemberPricingPolicy().termsFor(VehicleType.PEDAL_BIKE, incomplete));
    }

    @Test void serviceSelectsTheFirstSupportingPolicyInRegisteredOrder() throws Exception {
        var service = new PricingService(List.of(new StudentPricingPolicy(), new MemberPricingPolicy(), new CasualPricingPolicy()), config());
        assertEquals("STUDENT", service.quoteTerms(student(NOW.plusSeconds(60)), VehicleType.PEDAL_BIKE, NOW).category());
        assertEquals("MEMBER", service.quoteTerms(member("paid"), VehicleType.PEDAL_BIKE, NOW).category());
        assertEquals("CASUAL", service.quoteTerms(casual(), VehicleType.PEDAL_BIKE, NOW).category());
        // Casual first would shadow every discount, which is why it must be registered last.
        var casualFirst = new PricingService(List.of(new CasualPricingPolicy(), new StudentPricingPolicy()), config());
        assertEquals("CASUAL", casualFirst.quoteTerms(student(NOW.plusSeconds(60)), VehicleType.PEDAL_BIKE, NOW).category());
    }
}
