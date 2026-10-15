package ca.gocycle.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.gocycle.integration.DockingGateway;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatedDockingGatewayTest {

    private final UUID vehicle = UUID.randomUUID();

    @Test
    void confirmsUnlockAndLockWhenConfiguredToSucceed() {
        DockingGateway gateway = new SimulatedDockingGateway(true, true);
        assertTrue(gateway.requestUnlock("dock-1", vehicle, "req-1"));
        assertTrue(gateway.confirmLocked("dock-2", vehicle));
    }

    @Test
    void reportsFailureWhenConfiguredToFail() {
        DockingGateway gateway = new SimulatedDockingGateway(false, false);
        assertFalse(gateway.requestUnlock("dock-1", vehicle, "req-1"));
        assertFalse(gateway.confirmLocked("dock-2", vehicle));
    }

    @Test
    void unlockAndLockOutcomesAreIndependent() {
        var gateway = new SimulatedDockingGateway(true, false);
        assertTrue(gateway.requestUnlock("dock-1", vehicle, "req-1"));
        assertFalse(gateway.confirmLocked("dock-1", vehicle));
        assertTrue(gateway.getUnlockOutcome());
        assertFalse(gateway.getLockOutcome());
    }

    @Test
    void rejectsMalformedInput() {
        var gateway = new SimulatedDockingGateway(true, true);
        assertThrows(IllegalArgumentException.class, () -> gateway.requestUnlock(null, vehicle, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.requestUnlock(" ", vehicle, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.requestUnlock("dock-1", null, "r"));
        assertThrows(IllegalArgumentException.class, () -> gateway.requestUnlock("dock-1", vehicle, ""));
        assertThrows(IllegalArgumentException.class, () -> gateway.confirmLocked("", vehicle));
        assertThrows(IllegalArgumentException.class, () -> gateway.confirmLocked("dock-1", null));
    }
}
