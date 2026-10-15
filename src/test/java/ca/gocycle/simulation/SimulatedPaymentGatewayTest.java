package ca.gocycle.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.gocycle.data.PaymentResult;
import ca.gocycle.domain.PaymentMethod;
import ca.gocycle.integration.PaymentGateway;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    private static final BigDecimal FIFTY = new BigDecimal("50.00");

    @Test
    void successfulHoldChargeAndReleaseReturnReferences() {
        PaymentGateway gateway = new SimulatedPaymentGateway(true, true, true);
        PaymentResult hold = gateway.authorizeHold(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "hold-req");
        PaymentResult charge = gateway.charge(new BigDecimal("6.00"), "CAD", PaymentMethod.PHONE_WALLET, "charge-req");
        PaymentResult release = gateway.releaseHold(hold.reference(), "release-req");
        for (PaymentResult result : new PaymentResult[] {hold, charge, release}) {
            assertTrue(result.success());
            assertNotNull(result.reference());
            assertNull(result.failureCode());
        }
    }

    @Test
    void controlledFailuresReturnStructuredResultsInsteadOfThrowing() {
        var gateway = new SimulatedPaymentGateway(false, false, false);
        PaymentResult hold = gateway.authorizeHold(FIFTY, "CAD", PaymentMethod.TRANSIT_CARD, "h1");
        PaymentResult charge = gateway.charge(FIFTY, "CAD", PaymentMethod.TRANSIT_CARD, "c1");
        PaymentResult release = gateway.releaseHold("hold-h1", "r1");
        for (PaymentResult result : new PaymentResult[] {hold, charge, release}) {
            assertFalse(result.success());
            assertNull(result.reference());
            assertEquals("DECLINED", result.failureCode());
            assertNotNull(result.failureMessage());
        }
    }

    @Test
    void eachOperationUsesItsOwnOutcomeFlag() {
        var gateway = new SimulatedPaymentGateway(true, false, true);
        assertTrue(gateway.authorizeHold(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "a").success());
        assertFalse(gateway.charge(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "b").success());
        assertTrue(gateway.releaseHold("hold-a", "c").success());
        assertTrue(gateway.getHoldOutcome());
        assertFalse(gateway.getChargeOutcome());
        assertTrue(gateway.getReleaseOutcome());
    }

    @Test
    void sameRequestIdReplaysTheStoredResult() {
        var gateway = new SimulatedPaymentGateway(true, true, true);
        PaymentResult first = gateway.charge(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "same");
        PaymentResult again = gateway.charge(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "same");
        assertSame(first, again);
        assertEquals(1, gateway.getResultsByRequestId().size());
    }

    @Test
    void newRequestIdIsANewAttempt() {
        var gateway = new SimulatedPaymentGateway(true, true, true);
        gateway.charge(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "attempt-1");
        gateway.charge(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "attempt-2");
        assertEquals(2, gateway.getResultsByRequestId().size());
    }

    @Test
    void resultsSnapshotCannotBeModified() {
        var gateway = new SimulatedPaymentGateway(true, true, true);
        gateway.authorizeHold(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, "x");
        var snapshot = gateway.getResultsByRequestId();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("y", null));
    }

    @Test
    void rejectsMalformedInputWithoutStoringAnything() {
        var gateway = new SimulatedPaymentGateway(true, true, true);
        assertThrows(IllegalArgumentException.class, () -> gateway.authorizeHold(null, "CAD", PaymentMethod.CREDIT_CARD, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.authorizeHold(BigDecimal.ZERO, "CAD", PaymentMethod.CREDIT_CARD, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.charge(new BigDecimal("-1.00"), "CAD", PaymentMethod.CREDIT_CARD, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.charge(FIFTY, " ", PaymentMethod.CREDIT_CARD, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.charge(FIFTY, "CAD", null, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.charge(FIFTY, "CAD", PaymentMethod.CREDIT_CARD, null));
        assertThrows(IllegalArgumentException.class, () -> gateway.releaseHold(null, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.releaseHold("hold", ""));
        assertTrue(gateway.getResultsByRequestId().isEmpty());
    }
}
