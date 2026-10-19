package ca.gocycle.acceptance;

import ca.gocycle.application.TripService;
import ca.gocycle.config.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.rider.Rider;
import ca.gocycle.pricing.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static ca.gocycle.acceptance.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TripPricingTest {
    @Test void memberTripUsesTheMemberRate() throws Exception {
        var network = new Network();
        var rider = network.registered();
        network.riders.activateMembership(rider.getId(), PaymentMethod.CREDIT_CARD, network.origin.getZone());
        var trip = network.start(rider);
        assertEquals("MEMBER", trip.getPricingTerms().category());
        network.clock.advance(Duration.ofMinutes(2));
        network.trips.returnVehicle(trip.getId(), "destination");
        assertTrue(network.trips.settleTrip(trip.getId(), ReceiptChannel.APP).success());
        assertEquals(new BigDecimal("5.40"), trip.getFare().total());
    }

    @Test void studentTripUsesTheStudentRate() throws Exception {
        var network = new Network();
        var rider = network.registered();
        network.riders.verifyStudent(rider.getId(), "UCALGARY", false);
        var trip = network.start(rider);
        assertEquals("STUDENT", trip.getPricingTerms().category());
        assertEquals(new BigDecimal("0.20"), trip.getPricingTerms().perMinute());
    }

    @Test void tripCreationUsesRegisteredPolicies() throws Exception {
        var network = new Network();
        PricingPolicy policy = new PricingPolicy() {
            public boolean supports(Rider rider, Instant at, DeploymentConfiguration configuration) {
                return true;
            }
            public PricingConfiguration.FareTerms termsFor(VehicleType type, PricingConfiguration configuration) {
                return terms("ADDED");
            }
        };
        var pricing = new PricingService(List.of(policy, new CasualPricingPolicy()), network.config);
        var trips = new TripService(network.riders, network.stations, pricing, network.payments,
                network.docking, network.config, network.clock, network.lock);
        var trip = trips.startTrip(network.registered().getId(), network.vehicle.getId(), PaymentMethod.CREDIT_CARD);
        assertEquals("ADDED", trip.getPricingTerms().category());
    }
}
