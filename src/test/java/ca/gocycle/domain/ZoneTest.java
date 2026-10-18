package ca.gocycle.domain;

import ca.gocycle.data.DisplayConfiguration;
import ca.gocycle.domain.station.Zone;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneTest {
    private final DisplayConfiguration central = new DisplayConfiguration("#176B56", "#FFFFFF", "assets/central-logo.svg");
    private final DisplayConfiguration campus = new DisplayConfiguration("#000000", "#FFFFFF", "assets/campus-logo.svg");

    @Test void permitsOnlyConfiguredMethods() {
        var zone = new Zone("campus", Set.of(PaymentMethod.TRANSIT_CARD, PaymentMethod.PHONE_WALLET), campus);
        assertTrue(zone.permits(PaymentMethod.TRANSIT_CARD));
        assertTrue(zone.permits(PaymentMethod.PHONE_WALLET));
        assertFalse(zone.permits(PaymentMethod.CREDIT_CARD));
        assertThrows(IllegalArgumentException.class, () -> zone.permits(null));
    }

    @Test void constructorRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new Zone(null, Set.of(PaymentMethod.CREDIT_CARD), central));
        assertThrows(IllegalArgumentException.class, () -> new Zone(" ", Set.of(PaymentMethod.CREDIT_CARD), central));
        assertThrows(IllegalArgumentException.class, () -> new Zone("central", null, central));
        assertThrows(IllegalArgumentException.class, () -> new Zone("central", Set.of(), central));
        assertThrows(IllegalArgumentException.class, () -> new Zone("central", new HashSet<>(Arrays.asList(PaymentMethod.CREDIT_CARD, null)), central));
        assertThrows(IllegalArgumentException.class, () -> new Zone("central", Set.of(PaymentMethod.CREDIT_CARD), null));
    }

    @Test void updateReplacesMethodsAndBrandingTogether() {
        var zone = new Zone("central", EnumSet.allOf(PaymentMethod.class), central);
        zone.updateConfiguration(Set.of(PaymentMethod.TRANSIT_CARD), campus);
        assertEquals(Set.of(PaymentMethod.TRANSIT_CARD), zone.getAllowedPaymentMethods());
        assertFalse(zone.permits(PaymentMethod.CREDIT_CARD));
        assertEquals(campus, zone.getDisplay());
    }

    @Test void rejectedUpdateLeavesPreviousConfiguration() {
        var zone = new Zone("central", Set.of(PaymentMethod.CREDIT_CARD), central);
        assertThrows(IllegalArgumentException.class, () -> zone.updateConfiguration(Set.of(), campus));
        assertThrows(IllegalArgumentException.class, () -> zone.updateConfiguration(null, campus));
        assertThrows(IllegalArgumentException.class, () -> zone.updateConfiguration(Set.of(PaymentMethod.TRANSIT_CARD), null));
        assertEquals(Set.of(PaymentMethod.CREDIT_CARD), zone.getAllowedPaymentMethods());
        assertEquals(central, zone.getDisplay());
    }

    @Test void callerCollectionsCannotChangeTheZone() {
        var methods = new HashSet<>(Set.of(PaymentMethod.CREDIT_CARD));
        var zone = new Zone("central", methods, central);
        methods.add(PaymentMethod.TRANSIT_CARD);
        assertFalse(zone.permits(PaymentMethod.TRANSIT_CARD));
        assertThrows(UnsupportedOperationException.class, () -> zone.getAllowedPaymentMethods().add(PaymentMethod.TRANSIT_CARD));
        var update = new HashSet<>(Set.of(PaymentMethod.PHONE_WALLET));
        zone.updateConfiguration(update, central);
        update.clear();
        assertTrue(zone.permits(PaymentMethod.PHONE_WALLET));
    }
}
