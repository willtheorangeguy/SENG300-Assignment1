package ca.gocycle.domain;

import ca.gocycle.data.InterfacePreferences;
import ca.gocycle.domain.rider.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiderMembershipTest {
    private static final Instant START = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant END = START.plus(Duration.ofDays(30));
    private static final InterfacePreferences ENGLISH = new InterfacePreferences("en", false);

    private Rider rider(String institution, Instant until, Membership membership) {
        return new Rider(UUID.randomUUID(), "Student", "student@ucalgary.ca", LocalDate.of(2010, 1, 1),
                institution, until, membership, ENGLISH);
    }

    @Test void paidMembershipUsesInclusiveStartExclusiveEnd() {
        var m = new Membership(START, END, "payment-1");
        assertFalse(m.isActiveAt(START.minusNanos(1)));
        assertTrue(m.isActiveAt(START));
        assertTrue(m.isActiveAt(END.minusNanos(1)));
        assertFalse(m.isActiveAt(END));
        assertFalse(m.isActiveAt(END.plusNanos(1)));
        assertEquals("payment-1", m.getPaymentReference());
        assertEquals(Duration.ofDays(30), Duration.between(m.getValidFrom(), m.getValidUntil()));
        assertThrows(IllegalArgumentException.class, () -> m.isActiveAt(null));
    }

    @Test void unpaidMembershipNeverGrantsDiscountAndDurationIsNotHardCoded() {
        assertFalse(new Membership(START, END, null).isActiveAt(START));
        var shortMembership = new Membership(START, START.plusSeconds(60), "paid");
        assertTrue(shortMembership.isActiveAt(START.plusSeconds(59)));
        assertFalse(shortMembership.isActiveAt(START.plusSeconds(60)));
    }

    @Test void invalidMembershipInputsReject() {
        assertThrows(IllegalArgumentException.class, () -> new Membership(null, END, "paid"));
        assertThrows(IllegalArgumentException.class, () -> new Membership(START, null, "paid"));
        assertThrows(IllegalArgumentException.class, () -> new Membership(START, START, "paid"));
        assertThrows(IllegalArgumentException.class, () -> new Membership(END, START, "paid"));
        assertThrows(IllegalArgumentException.class, () -> new Membership(START, END, " "));
    }

    @Test void casualMemberAndStudentEligibilityRemainDistinct() {
        var casual = rider(null, null, null);
        assertFalse(casual.hasActiveMembership(START));
        assertFalse(casual.hasValidStudentEvidence(START));
        var member = rider(null, null, new Membership(START, END, "paid"));
        assertTrue(member.hasActiveMembership(START));
        assertFalse(member.hasActiveMembership(END));
        assertFalse(member.hasValidStudentEvidence(START));
        var student = rider("UCALGARY", END, null);
        assertTrue(student.hasValidStudentEvidence(END.minusNanos(1)));
        assertFalse(student.hasValidStudentEvidence(END));
        assertFalse(student.hasActiveMembership(START));
        assertEquals(LocalDate.of(2010, 1, 1), student.getBirthDate());
        assertThrows(IllegalArgumentException.class, () -> casual.hasActiveMembership(null));
        assertThrows(IllegalArgumentException.class, () -> casual.hasValidStudentEvidence(null));
    }

    @Test void overlappingPaidMembershipIsRejectedButExpiredEvidenceIsAllowed() {
        assertThrows(IllegalStateException.class,
            () -> rider("UCALGARY", END, new Membership(START, END, "paid")));
        var transitioned = rider("UCALGARY", START, new Membership(START, END, "paid"));
        assertFalse(transitioned.hasValidStudentEvidence(START));
        assertTrue(transitioned.hasActiveMembership(START));
        var unpaid = rider("UCALGARY", END, new Membership(START, END, null));
        assertTrue(unpaid.hasValidStudentEvidence(START));
        assertFalse(unpaid.hasActiveMembership(START));
    }

    @Test void preferenceChangesPreserveIdentityAndRejectMalformedValuesAtomically() {
        var r = rider(null, null, null);
        var id = r.getId();
        var french = new InterfacePreferences("fr-CA", true);
        r.updatePreferences(french);
        assertEquals(french, r.getPreferences());
        assertEquals(id, r.getId());
        assertEquals("Student", r.getName());
        assertEquals("student@ucalgary.ca", r.getEmail());
        for (var language : new String[] {null, "", " ", "bad_tag"}) {
            assertThrows(IllegalArgumentException.class, () -> r.updatePreferences(new InterfacePreferences(language, false)));
            assertEquals(french, r.getPreferences());
        }
        assertThrows(IllegalArgumentException.class, () -> r.updatePreferences(null));
        assertEquals(french, r.getPreferences());
        // The service, not this domain object, owns the configured language allow-list.
        r.updatePreferences(new InterfacePreferences("es", false));
        assertEquals("es", r.getPreferences().language());
    }

    @Test void incompleteEvidenceAndMalformedIdentityReject() {
        assertThrows(IllegalArgumentException.class, () -> rider("UCALGARY", null, null));
        assertThrows(IllegalArgumentException.class, () -> rider(null, END, null));
        assertThrows(IllegalArgumentException.class, () -> rider(" ", END, null));
        assertThrows(IllegalArgumentException.class, () -> new Rider(null, "Name", "a@b.ca", LocalDate.of(2000,1,1), null, null, null, ENGLISH));
        assertThrows(IllegalArgumentException.class, () -> new Rider(UUID.randomUUID(), " ", "a@b.ca", LocalDate.of(2000,1,1), null, null, null, ENGLISH));
        assertThrows(IllegalArgumentException.class, () -> new Rider(UUID.randomUUID(), "Name", "invalid", LocalDate.of(2000,1,1), null, null, null, ENGLISH));
        assertThrows(IllegalArgumentException.class, () -> new Rider(UUID.randomUUID(), "Name", "a@b.ca", null, null, null, null, ENGLISH));
        assertThrows(IllegalArgumentException.class, () -> new Rider(UUID.randomUUID(), "Name", "a@b.ca", LocalDate.of(2000,1,1), null, null, null, null));
    }
}
