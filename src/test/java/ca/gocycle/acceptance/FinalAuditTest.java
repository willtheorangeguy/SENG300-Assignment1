package ca.gocycle.acceptance;

import static ca.gocycle.acceptance.Fixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import ca.gocycle.application.PaymentService;
import ca.gocycle.application.StationService;
import ca.gocycle.application.TripService;
import ca.gocycle.data.PaymentResult;
import ca.gocycle.domain.*;
import ca.gocycle.domain.trip.Trip;
import ca.gocycle.integration.PaymentGateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

class FinalAuditTest {
    @Test
    void configuredPricingCategoryWorksWithoutChangingPricingService() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.readTree(java.nio.file.Path.of("config/gocycle.example.json").toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/pricing/rates"))
                .put("VIP", "0.10");
        var configuration =
                mapper.treeToValue(tree, ca.gocycle.config.DeploymentConfiguration.class);
        assertTrue(
                new ca.gocycle.config.ConfigurationValidator().validate(configuration).isEmpty());
        var policy =
                new ca.gocycle.pricing.PricingPolicy() {
                    public boolean supports(
                            ca.gocycle.domain.rider.Rider r,
                            java.time.Instant at,
                            ca.gocycle.config.DeploymentConfiguration c) {
                        return true;
                    }

                    public ca.gocycle.config.PricingConfiguration.FareTerms termsFor(
                            VehicleType type, ca.gocycle.config.PricingConfiguration c) {
                        return c.snapshot("VIP", type);
                    }
                };
        var pricing = new ca.gocycle.pricing.PricingService(List.of(policy), configuration);
        assertEquals(
                new BigDecimal("0.10"),
                pricing.quoteTerms(rider(), VehicleType.PEDAL_BIKE, NOW).perMinute());
    }

    @Test
    void consoleOffersMembershipActivationAfterLogin() throws Exception {
        var n = new Network();
        var dashboard = new ca.gocycle.presentation.OperatorDashboard(n.stations);
        var faults =
                new ca.gocycle.application.FaultService(
                        List.of(dashboard), n.stations, n.config, n.clock);
        var app =
                new ca.gocycle.presentation.ConsoleApplication(
                        n.config, n.riders, n.trips, faults, n.stations, dashboard);
        var input = System.in;
        var output = System.out;
        var rendered = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(
                    new java.io.ByteArrayInputStream(
                            ("register\n"
                                 + "Test Rider\n"
                                 + "2000-01-01\n"
                                 + "rider@ucalgary.ca\n"
                                 + "secret\n"
                                 + "CASUAL\n"
                                 + "CREDIT_CARD\n"
                                 + "login\n"
                                 + "rider@ucalgary.ca\n"
                                 + "secret\n"
                                 + "membership\n"
                                 + "origin CREDIT_CARD\n"
                                 + "quit\n")
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(
                    new java.io.PrintStream(
                            rendered, true, java.nio.charset.StandardCharsets.UTF_8));
            app.run();
        } finally {
            System.setIn(input);
            System.setOut(output);
        }
        assertTrue(
                rendered.toString(java.nio.charset.StandardCharsets.UTF_8)
                        .contains("Membership active until"));
        assertEquals(1, n.gateway.charges);
    }

    @Test
    void cancelledUnlockRetainsDepositForRecovery() throws Exception {
        var n = new Network();
        n.docking.unlock = false;
        n.gateway.release = false;
        var error = assertThrows(IllegalStateException.class, () -> n.start(n.registered()));
        var matcher =
                java.util.regex.Pattern.compile("trip ([a-f0-9-]{36})").matcher(error.getMessage());
        assertTrue(matcher.find());
        var id = UUID.fromString(matcher.group(1));
        var trip = n.trips.findTrip(id).orElseThrow();
        assertEquals(TripStatus.CANCELLED, trip.getStatus());
        assertEquals(DepositStatus.RELEASE_FAILED, trip.getDepositStatus());
        n.gateway.release = true;
        assertTrue(n.trips.settleTrip(id, ReceiptChannel.APP).success());
        assertEquals(DepositStatus.RELEASED, trip.getDepositStatus());
        assertEquals(0, n.gateway.charges);
        assertEquals(0, n.delivery.calls);
        assertNull(trip.getStartedAt());
        assertSame(n.vehicle, n.origin.getDockingPoints().getFirst().getVehicle());
    }

    @Test
    void expiredMembershipCanRenewForAnotherPaidPeriod() throws Exception {
        var n = new Network();
        var rider = n.registered();
        n.riders.activateMembership(rider.getId(), PaymentMethod.CREDIT_CARD, n.origin.getZone());
        n.clock.advance(Duration.ofDays(31));
        var renewed =
                n.riders.activateMembership(
                        rider.getId(), PaymentMethod.CREDIT_CARD, n.origin.getZone());
        assertEquals(n.clock.instant(), renewed.getValidFrom());
        assertEquals(n.clock.instant().plus(Duration.ofDays(30)), renewed.getValidUntil());
        assertTrue(rider.hasActiveMembership(n.clock.instant()));
        assertEquals(2, n.gateway.charges);
    }

    @ParameterizedTest
    @CsvSource({"0,1,1,0.00", "60,1,2,0.00", "7200,0,120,0.00", "7200,1,121,15.00"})
    void billsEveryStartedMinuteAndExactSurchargeBoundary(
            long seconds, int nanos, long minutes, String surcharge) throws Exception {
        Trip t =
                trip(
                        rider(),
                        bike(),
                        NOW,
                        NOW.plusSeconds(seconds).plusNanos(nanos),
                        TripStatus.ENDED,
                        PaymentStatus.PENDING,
                        DepositStatus.HELD,
                        terms("CASUAL"));
        var fare = pricing(config()).calculateFare(t);
        assertEquals(minutes, fare.billableMinutes());
        assertEquals(new BigDecimal(surcharge), fare.surcharge());
    }

    @Test
    void settlementRetriesFailedDepositWithoutRepeatingFare() throws Exception {
        var n = new Network();
        var t = n.start(n.registered());
        n.clock.advance(Duration.ofMinutes(1));
        n.gateway.release = false;
        n.trips.returnVehicle(t.getId(), "destination");
        assertEquals(DepositStatus.RELEASE_FAILED, t.getDepositStatus());
        n.gateway.release = true;
        assertTrue(n.trips.settleTrip(t.getId(), ReceiptChannel.APP).success());
        assertTrue(n.trips.settleTrip(t.getId(), ReceiptChannel.APP).success());
        assertEquals(DepositStatus.RELEASED, t.getDepositStatus());
        assertEquals(2, n.gateway.releases);
        assertEquals(1, n.gateway.charges);
        assertEquals(DepositStatus.RELEASED, n.delivery.delivered.getFirst().depositStatus());
    }

    @Test
    void closedStationRejectsStartBeforePaymentOrUnlock() throws Exception {
        var n = new Network();
        var closed = station("closed", 51, -114, false, List.of(dock("closed-dock", n.vehicle)));
        var stations =
                new StationService(
                        Map.of("closed", closed),
                        Map.of(n.vehicle.getId(), n.vehicle),
                        n.config,
                        n.clock,
                        n.lock);
        var trips =
                new TripService(
                        n.riders,
                        stations,
                        n.pricing,
                        n.payments,
                        n.docking,
                        n.config,
                        n.clock,
                        n.lock);
        assertThrows(
                IllegalStateException.class,
                () ->
                        trips.startTrip(
                                n.registered().getId(),
                                n.vehicle.getId(),
                                PaymentMethod.CREDIT_CARD));
        assertEquals(0, n.gateway.holds);
        assertEquals(0, n.docking.unlocks);
        assertEquals(VehicleAvailability.AVAILABLE, n.vehicle.getAvailability());
    }

    @Test
    void expiredRegistrationWindowCannotChargeForInactiveMembership() throws Exception {
        var n = new Network();
        var r = n.registered();
        n.clock.advance(Duration.ofDays(30));
        assertThrows(
                IllegalStateException.class,
                () ->
                        n.riders.activateMembership(
                                r.getId(), PaymentMethod.CREDIT_CARD, n.origin.getZone()));
        assertEquals(0, n.gateway.charges);
        assertNull(r.getMembership());
    }

    @Test
    void ambiguousFareRetryReusesSameKey() throws Exception {
        var n = new Network();
        List<String> keys = new ArrayList<>();
        PaymentGateway gateway =
                new PaymentGateway() {
                    public PaymentResult authorizeHold(
                            BigDecimal a, String c, PaymentMethod m, String key) {
                        throw new AssertionError("No new hold expected");
                    }

                    public PaymentResult releaseHold(String ref, String key) {
                        throw new AssertionError("No release expected");
                    }

                    public PaymentResult charge(
                            BigDecimal a, String c, PaymentMethod m, String key) {
                        keys.add(key);
                        if (keys.size() == 1)
                            return new PaymentResult(false, null, "DECLINED", "Try again");
                        if (keys.size() == 2)
                            throw new IllegalStateException(
                                    "Response lost after provider processed request");
                        return new PaymentResult(true, "paid", null, null);
                    }
                };
        var payments = new PaymentService(gateway, n.delivery, n.config, n.clock);
        var t = ended(61);
        var fare = n.pricing.calculateFare(t);
        assertFalse(payments.chargeFare(t, fare).success());
        assertThrows(IllegalStateException.class, () -> payments.chargeFare(t, fare));
        assertTrue(payments.chargeFare(t, fare).success());
        assertNotEquals(keys.get(0), keys.get(1));
        assertEquals(keys.get(1), keys.get(2));
    }
}
