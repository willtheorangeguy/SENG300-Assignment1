package ca.gocycle.domain;

import ca.gocycle.config.PricingConfiguration;
import ca.gocycle.data.*;
import ca.gocycle.domain.rider.Rider;
import ca.gocycle.domain.trip.Trip;
import ca.gocycle.domain.vehicle.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TripTest {
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final PricingConfiguration.FareTerms TERMS = new PricingConfiguration.FareTerms("CASUAL",
            new BigDecimal("5.00"), new BigDecimal("0.50"), new BigDecimal("50.00"), new BigDecimal("15.00"), "MINUTE", "UP", null);
    private static final FareBreakdown FARE = new FareBreakdown(new BigDecimal("5.00"), 2, new BigDecimal("1.00"),
            new BigDecimal("0.00"), new BigDecimal("6.00"));

    private Rider rider() {
        return new Rider(UUID.randomUUID(), "Test Rider", "rider@ucalgary.ca", LocalDate.of(2000, 1, 1), null, null, null,
                new InterfacePreferences("en", false));
    }
    private Trip pending() {
        return new Trip(UUID.randomUUID(), rider(), new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE),
                "origin", null, null, null, TripStatus.PENDING, PaymentStatus.NOT_DUE, DepositStatus.NONE, TERMS, null, null,
                PaymentMethod.CREDIT_CARD, Set.of(PaymentMethod.CREDIT_CARD, PaymentMethod.TRANSIT_CARD), null);
    }
    private Trip active() {
        var t = pending();
        t.recordDeposit(DepositStatus.HELD, "hold-1");
        t.beginAt(NOW);
        return t;
    }
    private Trip ended(long seconds) {
        var t = active();
        t.endAt(NOW.plusSeconds(seconds), "dock-1");
        return t;
    }

    @Test void beginSetsBillableStartOnceAndActivates() {
        var t = pending();
        assertNull(t.getStartedAt());
        assertEquals(Duration.ZERO, t.durationAt(NOW.plusSeconds(600)));
        t.beginAt(NOW);
        assertEquals(TripStatus.ACTIVE, t.getStatus());
        assertEquals(NOW, t.getStartedAt());
        assertThrows(IllegalStateException.class, () -> t.beginAt(NOW.plusSeconds(5)));
        assertEquals(NOW, t.getStartedAt());
        assertThrows(IllegalArgumentException.class, () -> pending().beginAt(null));
    }

    @Test void failedStartCancelsWithoutBillableTime() {
        var t = pending();
        t.cancelStart();
        t.cancelStart();
        assertEquals(TripStatus.CANCELLED, t.getStatus());
        assertNull(t.getStartedAt());
        assertEquals(Duration.ZERO, t.durationAt(NOW));
        assertThrows(IllegalStateException.class, () -> t.beginAt(NOW));
        assertThrows(IllegalStateException.class, () -> active().cancelStart());
    }

    @Test void activeDurationFollowsTheSuppliedTime() {
        var t = active();
        assertEquals(Duration.ofMinutes(11), t.durationAt(NOW.plus(Duration.ofMinutes(11))));
        assertThrows(IllegalArgumentException.class, () -> t.durationAt(NOW.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> t.durationAt(null));
    }

    @Test void endCapturesTimeAndDockOnce() {
        var t = ended(61);
        assertEquals(TripStatus.ENDED, t.getStatus());
        assertEquals(NOW.plusSeconds(61), t.getEndedAt());
        assertEquals("dock-1", t.getReturnDockId());
        // A repeated lock confirmation at the same dock keeps the original end.
        t.endAt(NOW.plusSeconds(300), "dock-1");
        assertEquals(NOW.plusSeconds(61), t.getEndedAt());
        assertEquals(Duration.ofSeconds(61), t.durationAt(NOW.plus(Duration.ofHours(5))));
        assertThrows(IllegalStateException.class, () -> t.endAt(NOW.plusSeconds(300), "dock-2"));
        assertEquals("dock-1", t.getReturnDockId());
    }

    @Test void endRejectsInvalidInputAndStates() {
        var t = active();
        assertThrows(IllegalArgumentException.class, () -> t.endAt(null, "dock-1"));
        assertThrows(IllegalArgumentException.class, () -> t.endAt(NOW, " "));
        assertThrows(IllegalArgumentException.class, () -> t.endAt(NOW.minusSeconds(1), "dock-1"));
        assertEquals(TripStatus.ACTIVE, t.getStatus());
        assertNull(t.getEndedAt());
        assertThrows(IllegalStateException.class, () -> pending().endAt(NOW, "dock-1"));
    }

    @Test void depositHoldAndReleaseAreNeverDuplicated() {
        var t = pending();
        assertThrows(IllegalStateException.class, () -> t.recordDeposit(DepositStatus.RELEASED, "release-1"));
        assertThrows(IllegalArgumentException.class, () -> t.recordDeposit(DepositStatus.HELD, null));
        t.recordDeposit(DepositStatus.HELD, "hold-1");
        t.recordDeposit(DepositStatus.HELD, "hold-1");
        assertThrows(IllegalStateException.class, () -> t.recordDeposit(DepositStatus.HELD, "hold-2"));
        assertThrows(IllegalStateException.class, () -> t.recordDeposit(DepositStatus.NONE, null));
        assertEquals("hold-1", t.getDepositReference());
        t.recordDeposit(DepositStatus.RELEASED, "release-1");
        t.recordDeposit(DepositStatus.RELEASED, "release-1");
        assertEquals(DepositStatus.RELEASED, t.getDepositStatus());
        assertThrows(IllegalStateException.class, () -> t.recordDeposit(DepositStatus.RELEASE_FAILED, null));
        assertThrows(IllegalStateException.class, () -> t.recordDeposit(DepositStatus.HELD, "hold-3"));
    }

    @Test void failedReleaseKeepsTheHoldReferenceAndDoesNotRestartCharges() {
        var t = ended(61);
        t.recordDeposit(DepositStatus.RELEASE_FAILED, null);
        assertEquals(DepositStatus.RELEASE_FAILED, t.getDepositStatus());
        assertEquals("hold-1", t.getDepositReference());
        assertEquals(TripStatus.ENDED, t.getStatus());
        assertEquals(Duration.ofSeconds(61), t.durationAt(NOW.plus(Duration.ofHours(1))));
        t.recordDeposit(DepositStatus.RELEASED, null);
        assertEquals(DepositStatus.RELEASED, t.getDepositStatus());
    }

    @Test void paymentRequiresAnEndedTripAndARecordedFare() {
        var t = active();
        assertThrows(IllegalStateException.class, () -> t.recordPayment(PaymentStatus.PENDING, null));
        assertThrows(IllegalStateException.class, () -> t.recordFare(FARE));
        var e = ended(61);
        assertThrows(IllegalStateException.class, () -> e.recordPayment(PaymentStatus.PAID, "charge-1"));
        assertThrows(IllegalArgumentException.class, () -> e.recordPayment(PaymentStatus.PAID, null));
        assertThrows(IllegalArgumentException.class, () -> e.recordPayment(null, null));
        assertThrows(IllegalArgumentException.class, () -> e.recordFare(null));
        assertEquals(PaymentStatus.NOT_DUE, e.getPaymentStatus());
    }

    @Test void failedPaymentCanBeRetriedButSuccessIsFinal() {
        var t = ended(61);
        var end = t.getEndedAt();
        t.recordFare(FARE);
        t.recordPayment(PaymentStatus.FAILED, null);
        assertEquals(TripStatus.ENDED, t.getStatus());
        t.recordPayment(PaymentStatus.PENDING, null);
        t.recordPayment(PaymentStatus.PAID, "charge-1");
        t.recordPayment(PaymentStatus.PAID, "charge-1");
        assertEquals(PaymentStatus.PAID, t.getPaymentStatus());
        assertEquals("charge-1", t.getPaymentReference());
        assertThrows(IllegalStateException.class, () -> t.recordPayment(PaymentStatus.PAID, "charge-2"));
        assertThrows(IllegalStateException.class, () -> t.recordPayment(PaymentStatus.FAILED, null));
        assertEquals(end, t.getEndedAt());
    }

    @Test void paidFareCannotChange() {
        var t = ended(61);
        t.recordFare(new FareBreakdown(new BigDecimal("5.00"), 1, new BigDecimal("0.50"), new BigDecimal("0.00"), new BigDecimal("5.50")));
        t.recordFare(FARE);
        t.recordPayment(PaymentStatus.PAID, "charge-1");
        t.recordFare(FARE);
        var different = new FareBreakdown(new BigDecimal("5.00"), 3, new BigDecimal("1.50"), new BigDecimal("0.00"), new BigDecimal("6.50"));
        assertThrows(IllegalStateException.class, () -> t.recordFare(different));
        assertEquals(FARE, t.getFare());
    }

    @Test void pricingTermsAndStartingMethodsAreSnapshots() {
        var methods = new HashSet<>(Set.of(PaymentMethod.CREDIT_CARD));
        var t = new Trip(UUID.randomUUID(), rider(), new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE),
                "origin", null, null, null, TripStatus.PENDING, PaymentStatus.NOT_DUE, DepositStatus.NONE, TERMS, null, null,
                PaymentMethod.CREDIT_CARD, methods, null);
        methods.clear();
        assertEquals(Set.of(PaymentMethod.CREDIT_CARD), t.getAllowedMethodsAtStart());
        assertThrows(UnsupportedOperationException.class, () -> t.getAllowedMethodsAtStart().add(PaymentMethod.TRANSIT_CARD));
        t.beginAt(NOW);
        assertSame(TERMS, t.getPricingTerms());
    }

    @Test void constructorRejectsInconsistentState() {
        var r = rider();
        var v = new PedalBike(UUID.randomUUID(), VehicleServiceStatus.IN_SERVICE, VehicleAvailability.AVAILABLE);
        var ok = Set.of(PaymentMethod.CREDIT_CARD);
        // Payment method not allowed in the starting zone.
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", null, null, null, TripStatus.PENDING,
                PaymentStatus.NOT_DUE, DepositStatus.NONE, TERMS, null, null, PaymentMethod.PHONE_WALLET, ok, null));
        // Missing pricing terms.
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", null, null, null, TripStatus.PENDING,
                PaymentStatus.NOT_DUE, DepositStatus.NONE, null, null, null, PaymentMethod.CREDIT_CARD, ok, null));
        // PENDING trips have no billable start.
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", null, NOW, null, TripStatus.PENDING,
                PaymentStatus.NOT_DUE, DepositStatus.NONE, TERMS, null, null, PaymentMethod.CREDIT_CARD, ok, null));
        // ENDED trips need an end and a dock, in order.
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", null, NOW, NOW.plusSeconds(1), TripStatus.ENDED,
                PaymentStatus.PENDING, DepositStatus.HELD, TERMS, "hold-1", null, PaymentMethod.CREDIT_CARD, ok, null));
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", "dock-1", NOW, NOW.minusSeconds(1), TripStatus.ENDED,
                PaymentStatus.PENDING, DepositStatus.HELD, TERMS, "hold-1", null, PaymentMethod.CREDIT_CARD, ok, null));
        // A held deposit needs its reference; a paid trip needs a fare.
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", null, NOW, null, TripStatus.ACTIVE,
                PaymentStatus.NOT_DUE, DepositStatus.HELD, TERMS, null, null, PaymentMethod.CREDIT_CARD, ok, null));
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", "dock-1", NOW, NOW.plusSeconds(1), TripStatus.ENDED,
                PaymentStatus.PAID, DepositStatus.RELEASED, TERMS, "hold-1", "charge-1", PaymentMethod.CREDIT_CARD, ok, null));
        // Payment is not due before the trip ends.
        assertThrows(IllegalArgumentException.class, () -> new Trip(UUID.randomUUID(), r, v, "origin", null, NOW, null, TripStatus.ACTIVE,
                PaymentStatus.PENDING, DepositStatus.HELD, TERMS, "hold-1", null, PaymentMethod.CREDIT_CARD, ok, null));
    }
}
