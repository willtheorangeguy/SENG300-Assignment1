package ca.gocycle.domain.vehicle;

import ca.gocycle.domain.VehicleAvailability;
import ca.gocycle.domain.VehicleServiceStatus;
import ca.gocycle.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Physical availability is independent of service status: a fault never ends a ride. */
public abstract class Vehicle {
    private final UUID id;
    private final VehicleType type;
    private VehicleServiceStatus serviceStatus;
    private VehicleAvailability availability;

    /** @throws IllegalArgumentException if any required attribute is null */
    public Vehicle(UUID id, VehicleType type, VehicleServiceStatus serviceStatus, VehicleAvailability availability) {
        if (id == null || type == null || serviceStatus == null || availability == null) {
            throw new IllegalArgumentException("Vehicle attributes must not be null");
        }
        this.id = id;
        this.type = type;
        this.serviceStatus = serviceStatus;
        this.availability = availability;
    }

    public UUID getId() { return id; }
    public VehicleType getType() { return type; }

    /** @throws IllegalArgumentException if the evaluation time is null */
    public synchronized boolean isRentable(Instant at) {
        if (at == null) throw new IllegalArgumentException("Evaluation time is required");
        return serviceStatus == VehicleServiceStatus.IN_SERVICE
                && availability == VehicleAvailability.AVAILABLE;
    }

    /** Changes future eligibility without modifying the physical state. */
    public synchronized void setServiceStatus(VehicleServiceStatus status) {
        if (status == null) throw new IllegalArgumentException("Service status is required");
        serviceStatus = status;
    }

    /** Atomically reserves a currently rentable vehicle; rejects competing requests. */
    public synchronized void reserve() {
        if (!isRentable(Instant.now())) throw new IllegalStateException("Vehicle is not rentable");
        availability = VehicleAvailability.RESERVED;
    }

    /** @throws IllegalStateException unless a failed start has a reservation to release */
    public synchronized void releaseReservation() {
        transition(VehicleAvailability.RESERVED, VehicleAvailability.AVAILABLE);
    }

    /** The caller must confirm physical unlocking before recording a successful start. */
    public synchronized void markRented() {
        transition(VehicleAvailability.RESERVED, VehicleAvailability.RENTED);
    }

    /** The caller must validate the destination before relocating a docked vehicle. */
    public synchronized void markRelocating() {
        transition(VehicleAvailability.AVAILABLE, VehicleAvailability.RELOCATING);
    }

    /** The caller must confirm locking first. Repeated docking confirmation is harmless. */
    public synchronized void markDocked() {
        if (availability == VehicleAvailability.RESERVED) {
            throw new IllegalStateException("Release the reservation before recording docking");
        }
        availability = VehicleAvailability.AVAILABLE;
    }

    private void transition(VehicleAvailability expected, VehicleAvailability next) {
        if (availability != expected) {
            throw new IllegalStateException("Expected " + expected + " but vehicle is " + availability);
        }
        availability = next;
    }

    public synchronized VehicleServiceStatus getServiceStatus() { return serviceStatus; }
    public synchronized VehicleAvailability getAvailability() { return availability; }
}
