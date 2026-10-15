package ca.gocycle.simulation;

import ca.gocycle.integration.DockingGateway;

import java.util.UUID;

/**
 * A pretend docking-station controller for development and tests.
 *
 * <p>The two constructor flags decide whether every unlock request and every lock confirmation
 * succeeds. A {@code false} unlock means billing must not start; a {@code false} lock
 * confirmation means the trip stays active.
 */
public class SimulatedDockingGateway implements DockingGateway {

    private final boolean unlockOutcome;
    private final boolean lockOutcome;

    /**
     * @param unlockOutcome true if every unlock request is confirmed
     * @param lockOutcome   true if every lock check reports "locked"
     */
    public SimulatedDockingGateway(boolean unlockOutcome, boolean lockOutcome) {
        this.unlockOutcome = unlockOutcome;
        this.lockOutcome = lockOutcome;
    }

    /**
     * @return the configured unlock outcome; false means billing cannot start
     * @throws IllegalArgumentException if the dock ID or request ID is blank, or the vehicle ID is null
     */
    @Override
    public boolean requestUnlock(String dockId, UUID vehicleId, String requestId) {
        requireDockAndVehicle(dockId, vehicleId);
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return unlockOutcome;
    }

    /**
     * @return the configured lock outcome; false keeps the trip active
     * @throws IllegalArgumentException if the dock ID is blank or the vehicle ID is null
     */
    @Override
    public boolean confirmLocked(String dockId, UUID vehicleId) {
        requireDockAndVehicle(dockId, vehicleId);
        return lockOutcome;
    }

    public boolean getUnlockOutcome() {
        return unlockOutcome;
    }

    public boolean getLockOutcome() {
        return lockOutcome;
    }

    private static void requireDockAndVehicle(String dockId, UUID vehicleId) {
        if (dockId == null || dockId.isBlank()) {
            throw new IllegalArgumentException("dockId must not be blank");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("vehicleId must not be null");
        }
    }
}
