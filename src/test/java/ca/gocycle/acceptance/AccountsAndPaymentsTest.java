package ca.gocycle.acceptance;

import ca.gocycle.domain.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.data.*;
import ca.gocycle.application.*;
import org.junit.jupiter.api.*;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ca.gocycle.acceptance.Fixtures.*;

class AccountsAndPaymentsTest {
    @Test @Tag("pending-implementation") void invalidRegistrationRejects() throws Exception {
        var n=new Network();assertThrows(IllegalArgumentException.class,()->n.riders.register("","bad-email",LocalDate.of(2000,1,1)));
        assertThrows(IllegalArgumentException.class,()->n.riders.register("Test","r@ucalgary.ca",LocalDate.of(2100,1,1)));
    }
    @Test @Tag("pending-implementation") void noMinimumAgeForStudent() throws Exception {
        var n=new Network();var r=n.riders.register("Student","student@ucalgary.ca",LocalDate.of(2010,1,1));
        n.riders.verifyStudent(r.getId(),"UCALGARY",true);assertTrue(r.hasValidStudentEvidence(NOW));
    }
    @Test @Tag("pending-implementation") void studentNeedsUniversityEmail() throws Exception {
        var n=new Network();var r=n.riders.register("Student","student@example.com",LocalDate.of(2000,1,1));
        assertThrows(IllegalArgumentException.class,()->n.riders.verifyStudent(r.getId(),"UCALGARY",false));
    }
    @Test @Tag("pending-implementation") void paidMembershipLastsThirtyDaysFromRegistration() throws Exception {
        var n=new Network();var r=n.registered();n.clock.advance(Duration.ofDays(2));n.gateway.charge=false;
        assertThrows(IllegalStateException.class,()->n.riders.activateMembership(r.getId(),PaymentMethod.CREDIT_CARD,n.origin.getZone()));
        assertFalse(r.hasActiveMembership(NOW));n.gateway.charge=true;
        var membership=n.riders.activateMembership(r.getId(),PaymentMethod.CREDIT_CARD,n.origin.getZone());
        assertTrue(membership.isActiveAt(n.clock.instant()));assertEquals(NOW,membership.getValidFrom());assertEquals(NOW.plus(Duration.ofDays(30)),membership.getValidUntil());
        assertFalse(membership.isActiveAt(membership.getValidUntil()));
    }
    @Test @Tag("pending-implementation") void studentCannotAlsoBecomeMember() throws Exception {
        var n=new Network();var r=n.registered();n.riders.verifyStudent(r.getId(),"UCALGARY",false);
        assertThrows(IllegalStateException.class,()->n.riders.activateMembership(r.getId(),PaymentMethod.CREDIT_CARD,n.origin.getZone()));assertEquals(0,n.gateway.charges);
    }
    @Test @Tag("pending-implementation") void forbiddenZoneMethodRejectsBeforeHold() throws Exception {
        var n=new Network();var t=ended(61);var z=zone("restricted",Set.of(PaymentMethod.TRANSIT_CARD));
        assertThrows(IllegalStateException.class,()->n.payments.holdDeposit(t,z));assertEquals(0,n.gateway.holds);
    }
    @Test @Tag("pending-implementation") void fareFailureLeavesEndedAndWithholdsReceipt() throws Exception {
        var n=new Network();var t=ended(61);n.gateway.charge=false;var fare=n.pricing.calculateFare(t);
        assertFalse(n.payments.chargeFare(t,fare).success());assertEquals(TripStatus.ENDED,t.getStatus());assertEquals(PaymentStatus.FAILED,t.getPaymentStatus());
        assertThrows(IllegalStateException.class,()->n.payments.sendReceipt(t,ReceiptChannel.APP));assertEquals(0,n.delivery.calls);
    }
    @Test @Tag("pending-implementation") void releaseFailureDoesNotRestartCharges() throws Exception {
        var n=new Network();var t=ended(61);n.gateway.release=false;
        assertFalse(n.payments.releaseDeposit(t).success());assertEquals(TripStatus.ENDED,t.getStatus());
        assertEquals(DepositStatus.RELEASE_FAILED,t.getDepositStatus());assertEquals(Duration.ofSeconds(61),t.durationAt(NOW.plusSeconds(3600)));
    }
    @Test @Tag("pending-implementation") void failedFareRetryCanAdvanceAttemptAndSuccessIsNeverRepeated() throws Exception {
        var n=new Network();var t=ended(61);var f=n.pricing.calculateFare(t);n.gateway.charge=false;
        assertFalse(n.payments.chargeFare(t,f).success());n.gateway.charge=true;
        assertTrue(n.payments.chargeFare(t,f).success());assertTrue(n.payments.chargeFare(t,f).success());assertEquals(2,n.gateway.charges);
    }
    @Test @Tag("pending-implementation") void successfulSettlementAndReceiptAreIdempotent() throws Exception {
        var n=new Network();var t=ended(61);var f=n.pricing.calculateFare(t);
        assertTrue(n.payments.chargeFare(t,f).success());assertTrue(n.payments.chargeFare(t,f).success());assertEquals(1,n.gateway.charges);
        assertTrue(n.payments.releaseDeposit(t).success());assertTrue(n.payments.releaseDeposit(t).success());assertEquals(1,n.gateway.releases);
        assertTrue(n.payments.sendReceipt(t,ReceiptChannel.APP));assertTrue(n.payments.sendReceipt(t,ReceiptChannel.APP));assertEquals(1,n.delivery.delivered.size());
        assertEquals(new BigDecimal("6.00"),n.delivery.delivered.getFirst().fare().total());
    }
    @Test @Tag("pending-implementation") void failedDeliveryCanRetryWithoutRepeatingPayment() throws Exception {
        var n=new Network();var t=ended(61);n.payments.chargeFare(t,n.pricing.calculateFare(t));n.delivery.succeeds=false;
        assertFalse(n.payments.sendReceipt(t,ReceiptChannel.EMAIL));n.delivery.succeeds=true;
        assertTrue(n.payments.sendReceipt(t,ReceiptChannel.EMAIL));assertEquals(1,n.gateway.charges);assertEquals(1,n.delivery.delivered.size());
    }
}
