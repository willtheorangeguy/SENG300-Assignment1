package ca.gocycle.acceptance;

import ca.gocycle.data.FareBreakdown;
import ca.gocycle.data.PaymentResult;
import ca.gocycle.data.Receipt;
import ca.gocycle.domain.DepositStatus;
import ca.gocycle.domain.ReceiptChannel;
import ca.gocycle.simulation.SimulatedReceiptDelivery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRecordsTest {
    @Test
    void fareBreakdownRequiresNonnegativeValues() {
        var fare = new FareBreakdown(new BigDecimal("5.00"), 2, new BigDecimal("1.00"),
                BigDecimal.ZERO, new BigDecimal("6.00"));
        assertEquals(2, fare.billableMinutes());
        assertThrows(IllegalArgumentException.class, () -> new FareBreakdown(
                new BigDecimal("5.00"), -1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00")));
        assertThrows(IllegalArgumentException.class, () -> new FareBreakdown(
                null, 1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00")));
    }

    @Test
    void paymentResultRequiresFieldsForItsOutcome() {
        assertTrue(new PaymentResult(true, "charge-1", null, null).success());
        assertFalse(new PaymentResult(false, null, "DECLINED", "Payment declined").success());
        assertThrows(IllegalArgumentException.class, () -> new PaymentResult(false, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PaymentResult(true, null, null, null));
    }

    @Test
    void receiptRequiresAllFields() {
        var receipt = receipt();
        assertEquals("CAD", receipt.currency());
        assertThrows(IllegalArgumentException.class, () -> new Receipt(null, receipt.tripId(),
                receipt.paidAt(), receipt.currency(), receipt.fare(), receipt.depositStatus()));
    }

    @Test
    void simulatedDeliveryRecordsOnlySuccessfulDeliveries() {
        var receipt = receipt();
        var rider = Fixtures.rider();
        var successful = new SimulatedReceiptDelivery(true);
        assertTrue(successful.deliver(receipt, ReceiptChannel.EMAIL, rider));
        assertEquals(java.util.List.of(receipt), successful.getDeliveredReceipts());
        assertThrows(UnsupportedOperationException.class, () -> successful.getDeliveredReceipts().clear());

        var failed = new SimulatedReceiptDelivery(false);
        assertFalse(failed.deliver(receipt, ReceiptChannel.APP, rider));
        assertTrue(failed.getDeliveredReceipts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> successful.deliver(receipt, null, rider));
    }

    private Receipt receipt() {
        return new Receipt(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-01T12:00:00Z"),
                "CAD", new FareBreakdown(new BigDecimal("5.00"), 2, new BigDecimal("1.00"),
                BigDecimal.ZERO, new BigDecimal("6.00")), DepositStatus.RELEASED);
    }
}
