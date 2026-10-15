package ca.gocycle.simulation;

import ca.gocycle.data.Receipt;
import ca.gocycle.domain.ReceiptChannel;
import ca.gocycle.domain.rider.Rider;
import ca.gocycle.integration.ReceiptDelivery;

import java.util.ArrayList;
import java.util.List;

/**
 * A pretend receipt sender (app or email) for development and tests.
 *
 * <p>When the constructor flag is true, every receipt is "delivered" and remembered. When it is
 * false, delivery reports failure and nothing is remembered. Either way the payment itself is
 * not affected.
 */
public class SimulatedReceiptDelivery implements ReceiptDelivery {

    private final boolean deliveryOutcome;
    private final List<Receipt> deliveredReceipts;

    /** @param deliveryOutcome true if every delivery succeeds */
    public SimulatedReceiptDelivery(boolean deliveryOutcome) {
        this.deliveryOutcome = deliveryOutcome;
        this.deliveredReceipts = new ArrayList<>();
    }

    /**
     * @return true if the receipt was delivered, false if the simulation is set to fail
     * @throws IllegalArgumentException if the receipt, channel or rider is null
     */
    @Override
    public synchronized boolean deliver(Receipt receipt, ReceiptChannel channel, Rider rider) {
        if (receipt == null) {
            throw new IllegalArgumentException("receipt must not be null");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        if (rider == null) {
            throw new IllegalArgumentException("rider must not be null");
        }
        if (!deliveryOutcome) {
            return false;
        }
        deliveredReceipts.add(receipt);
        return true;
    }

    public boolean getDeliveryOutcome() {
        return deliveryOutcome;
    }

    /** @return an immutable snapshot of the receipts delivered so far, oldest first */
    public synchronized List<Receipt> getDeliveredReceipts() {
        return List.copyOf(deliveredReceipts);
    }
}
