package ca.gocycle.acceptance;

import ca.gocycle.domain.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.data.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ca.gocycle.acceptance.Fixtures.*;

class TripLifecycleTest {
    @Test @Tag("pending-implementation") void pendingStartHasNoBillableTimestamp() {
        var t=trip(rider(),bike(),null,null,TripStatus.PENDING,PaymentStatus.NOT_DUE,DepositStatus.NONE,terms("CASUAL"));
        t.beginAt(NOW);assertEquals(TripStatus.ACTIVE,t.getStatus());assertEquals(NOW,t.getStartedAt());
    }
    @Test @Tag("pending-implementation") void failedStartCancelsWithoutBillableTime() {
        var t=trip(rider(),bike(),null,null,TripStatus.PENDING,PaymentStatus.NOT_DUE,DepositStatus.NONE,terms("CASUAL"));
        t.cancelStart();assertEquals(TripStatus.CANCELLED,t.getStatus());assertNull(t.getStartedAt());
    }
    @Test @Tag("pending-implementation") void holdFailureDoesNotUnlock() throws Exception {
        var n=new Network();var r=n.registered();n.gateway.hold=false;
        assertThrows(IllegalStateException.class,()->n.start(r));assertEquals(0,n.docking.unlocks);
        assertEquals(VehicleAvailability.AVAILABLE,n.vehicle.getAvailability());
    }
    @Test @Tag("pending-implementation") void unlockFailureReleasesHoldAndReservation() throws Exception {
        var n=new Network();var r=n.registered();n.docking.unlock=false;
        assertThrows(IllegalStateException.class,()->n.start(r));assertEquals(1,n.gateway.holds);assertEquals(1,n.gateway.releases);
        assertEquals(VehicleAvailability.AVAILABLE,n.vehicle.getAvailability());
    }
    @Test @Tag("pending-implementation") void duplicateStartCannotCreateSecondHold() throws Exception {
        var n=new Network();var r=n.registered();var t=n.start(r);var another=n.riders.register("Another","another@ucalgary.ca",LocalDate.of(2000,1,1));
        assertThrows(IllegalStateException.class,()->n.start(another));assertEquals(1,n.gateway.holds);
        assertEquals(t.getId(),n.trips.findTrip(t.getId()).orElseThrow().getId());
    }
    @Test @Tag("pending-implementation") void lockFailurePreservesActiveCharges() throws Exception {
        var n=new Network();var t=n.start(n.registered());n.clock.advance(Duration.ofMinutes(10));n.docking.lock=false;
        assertThrows(IllegalStateException.class,()->n.trips.returnVehicle(t.getId(),"destination"));
        assertEquals(TripStatus.ACTIVE,t.getStatus());assertNull(t.getEndedAt());assertEquals(0,n.gateway.releases);
        n.clock.advance(Duration.ofMinutes(1));assertEquals(Duration.ofMinutes(11),t.durationAt(n.clock.instant()));
    }
    @Test @Tag("pending-implementation") void confirmedReturnEndsOnceAndReleasesHold() throws Exception {
        var n=new Network();var t=n.start(n.registered());n.clock.advance(Duration.ofSeconds(61));
        assertTrue(n.trips.returnVehicle(t.getId(),"destination").isEmpty());assertEquals(TripStatus.ENDED,t.getStatus());
        var end=t.getEndedAt();assertEquals(n.clock.instant(),end);assertEquals(1,n.gateway.releases);
        n.clock.advance(Duration.ofMinutes(3));n.trips.returnVehicle(t.getId(),"destination");
        assertEquals(end,t.getEndedAt());assertEquals(1,n.gateway.releases);assertEquals(Duration.ofSeconds(61),t.durationAt(n.clock.instant()));
    }
    @Test @Tag("pending-implementation") void fullStationRedirectsWithoutEnding() throws Exception {
        var n=new Network();var t=n.start(n.registered());
        n.destination.getDockingPoints().getFirst().attach(bike(),true);
        var redirect=n.trips.returnVehicle(t.getId(),"destination");assertEquals("origin",redirect.orElseThrow().getId());
        assertEquals(TripStatus.ACTIVE,t.getStatus());assertNull(t.getEndedAt());assertEquals(0,n.gateway.releases);
    }
    @Test @Tag("pending-implementation") void noUsableStationKeepsTripActive() throws Exception {
        var n=new Network();var t=n.start(n.registered());
        n.destination.getDockingPoints().getFirst().attach(bike(),true);n.origin.getDockingPoints().getFirst().attach(bike(),true);
        assertThrows(IllegalStateException.class,()->n.trips.returnVehicle(t.getId(),"destination"));assertEquals(TripStatus.ACTIVE,t.getStatus());
    }
    @Test @Tag("pending-implementation") void tripKeepsQuoteAndStartingZoneMethods() throws Exception {
        var n=new Network();var r=n.registered();var t=n.start(r);var quote=t.getPricingTerms();var methods=t.getAllowedMethodsAtStart();
        n.stations.updateZone("central",Set.of(PaymentMethod.TRANSIT_CARD),new DisplayConfiguration("#000000","#FFFFFF","assets/central-logo.svg"));
        n.riders.verifyStudent(r.getId(),"UCALGARY",false);
        assertEquals(quote,t.getPricingTerms());assertEquals("CASUAL",t.getPricingTerms().category());assertEquals(methods,t.getAllowedMethodsAtStart());
        assertTrue(t.getAllowedMethodsAtStart().contains(PaymentMethod.CREDIT_CARD));
        n.clock.advance(Duration.ofSeconds(61));n.trips.returnVehicle(t.getId(),"destination");
        assertEquals(new java.math.BigDecimal("6.00"),n.pricing.calculateFare(t).total());
    }
    @Test @Tag("pending-implementation") void crossZoneReturnUsesStartingZoneSnapshot() throws Exception {
        var n=new Network();var t=n.start(n.registered());
        n.destination.getZone().updateConfiguration(Set.of(PaymentMethod.TRANSIT_CARD),n.destination.getZone().getDisplay());
        n.clock.advance(Duration.ofMinutes(1));assertTrue(n.trips.returnVehicle(t.getId(),"destination").isEmpty());
        assertEquals(PaymentMethod.CREDIT_CARD,t.getPaymentMethod());assertTrue(t.getAllowedMethodsAtStart().contains(PaymentMethod.CREDIT_CARD));
    }
}
