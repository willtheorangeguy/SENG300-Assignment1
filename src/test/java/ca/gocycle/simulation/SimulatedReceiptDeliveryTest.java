package ca.gocycle.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.gocycle.data.FareBreakdown;
import ca.gocycle.data.InterfacePreferences;
import ca.gocycle.data.Receipt;
import ca.gocycle.domain.DepositStatus;
import ca.gocycle.domain.ReceiptChannel;
import ca.gocycle.domain.rider.Rider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatedReceiptDeliveryTest {

    private final Rider rider = new Rider(UUID.randomUUID(), "Test Rider", "rider@ucalgary.ca",
            LocalDate.of(2000, 1, 1), null, null, null, new InterfacePreferences("en", false));

    private Receipt receipt() {
        var fare = new FareBreakdown(new BigDecimal("5.00"), 10, new BigDecimal("5.00"), BigDecimal.ZERO.setScale(2), new BigDecimal("10.00"));
        return new Receipt(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-01T12:00:00Z"), "CAD", fare, DepositStatus.RELEASED);
    }

    @Test
    void successfulDeliveryIsRecordedInOrder() {
        var delivery = new SimulatedReceiptDelivery(true);
        Receipt first = receipt();
        Receipt second = receipt();
        assertTrue(delivery.deliver(first, ReceiptChannel.EMAIL, rider));
        assertTrue(delivery.deliver(second, ReceiptChannel.APP, rider));
        assertEquals(List.of(first, second), delivery.getDeliveredReceipts());
        assertTrue(delivery.getDeliveryOutcome());
    }

    @Test
    void failedDeliveryReturnsFalseAndRecordsNothing() {
        var delivery = new SimulatedReceiptDelivery(false);
        assertFalse(delivery.deliver(receipt(), ReceiptChannel.EMAIL, rider));
        assertTrue(delivery.getDeliveredReceipts().isEmpty());
    }

    @Test
    void deliveredReceiptsSnapshotCannotBeModified() {
        var delivery = new SimulatedReceiptDelivery(true);
        delivery.deliver(receipt(), ReceiptChannel.APP, rider);
        var snapshot = delivery.getDeliveredReceipts();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void rejectsMissingArguments() {
        var delivery = new SimulatedReceiptDelivery(true);
        assertThrows(IllegalArgumentException.class, () -> delivery.deliver(null, ReceiptChannel.APP, rider));
        assertThrows(IllegalArgumentException.class, () -> delivery.deliver(receipt(), null, rider));
        assertThrows(IllegalArgumentException.class, () -> delivery.deliver(receipt(), ReceiptChannel.APP, null));
        assertTrue(delivery.getDeliveredReceipts().isEmpty());
    }
}
