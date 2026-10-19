package ca.gocycle.application;

import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ca.gocycle.config.*;
import ca.gocycle.data.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.fault.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;

public class TripService {
    private final Map<UUID, Trip> trips;
    private final RiderService riders;
    private final StationService stations;
    private final PricingService pricing;
    private final PaymentService payments;
    private final DockingGateway docking;
    private final DeploymentConfiguration configuration;
    private final Clock clock;
    private final ReentrantLock coordinationLock;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public TripService(RiderService riders, StationService stations, PricingService pricing, PaymentService payments, DockingGateway docking, DeploymentConfiguration configuration, Clock clock, ReentrantLock coordinationLock) {
        this.trips = new LinkedHashMap<>();
        this.riders = riders;
        this.stations = stations;
        this.pricing = pricing;
        this.payments = payments;
        this.docking = docking;
        this.configuration = configuration;
        this.clock = clock;
        this.coordinationLock = coordinationLock;
    }

    /**
     * Validate rider, shared vehicle state, fresh battery and starting-zone method before hold/unlock. Serialize through the shared coordination lock. Failed hold does not unlock; failed unlock compensates the hold and reservation.
     * @param riderId required input; validate malformed values before mutation
     * @param vehicleId required input; validate malformed values before mutation
     * @param method required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Trip startTrip(UUID riderId, UUID vehicleId, PaymentMethod method) {

    if (riderId == null || vehicleId == null || method == null) {
        throw new IllegalArgumentException("Invalid trip information");
    }
    coordinationLock.lock();

try {

   Rider rider = riders.findRider(riderId)
    .orElseThrow(() -> new NoSuchElementException("Rider not found"));

long activeTrips = trips.values().stream()
    .filter(trip -> trip.getRider().getId().equals(riderId))
    .filter(trip -> trip.getStatus() == TripStatus.ACTIVE || trip.getStatus() == TripStatus.PENDING)
    .count();
if (activeTrips >= configuration.getRiders().maximumActiveTrips()) {
    throw new IllegalStateException("Rider already has an active trip");
}

Vehicle vehicle = stations.findVehicle(vehicleId)
    .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));

    DockingPoint dock = stations.findVehicleDock(vehicleId)
    .orElseThrow(() -> new IllegalStateException("Vehicle is not docked"));

    if (!dock.getLockConfirmed()) {
    throw new IllegalStateException("Vehicle is not securely docked");
}

Station startingStation = null;

for (Station station : stations.listStations()) {
    if (station.getDockingPoints().contains(dock)) {
        startingStation = station;
        break;
    }
}

if (startingStation == null) {
    throw new IllegalStateException("Starting station not found");
}
Zone startingZone = startingStation.getZone();

if (!startingZone.getAllowedPaymentMethods().contains(method)) {
    throw new IllegalStateException("Payment method not allowed in starting zone");
}

    if (!vehicle.isRentable(clock.instant())) {
    throw new IllegalStateException("Vehicle is not available for rental");
}

Trip trip = new Trip(
    UUID.randomUUID(),
    rider,
    vehicle,
    startingStation.getId(),
    null,
    null,
    null,
    TripStatus.PENDING,
    PaymentStatus.NOT_DUE,
    DepositStatus.NONE,
    pricing.quoteTerms(rider, vehicle.getType(), clock.instant()),
    null,
    null,
    method,
    startingZone.getAllowedPaymentMethods(),
    null
);





vehicle.reserve(clock.instant());

try {
    PaymentResult depositResult = payments.holdDeposit(trip, startingZone);

    if (!depositResult.success()) {
        throw new IllegalStateException("Deposit hold failed");
    }

    trip.recordDeposit(DepositStatus.HELD, depositResult.reference());

    boolean unlocked = docking.requestUnlock(
        dock.getId(),
        vehicleId,
        "unlock-" + trip.getId()
    );

    if (!unlocked) {
        throw new IllegalStateException("Vehicle unlock failed");
    }
dock.detach();
    vehicle.markRented();
    trip.beginAt(clock.instant());

    trips.put(trip.getId(), trip);

    return trip;

} catch (RuntimeException e) {

    if (trip.getStatus() == TripStatus.PENDING) {
        trip.cancelStart();
    }

    if (trip.getDepositStatus() == DepositStatus.HELD) {
        try {
            PaymentResult release = payments.getGateway().releaseHold(
                trip.getDepositReference(),
                "cancel-" + trip.getId()
            );

            if (release.success()) {
                trip.recordDeposit(DepositStatus.RELEASED, null);
            } else {
                trip.recordDeposit(DepositStatus.RELEASE_FAILED, null);
            }
        } catch (RuntimeException releaseError) {
            trip.recordDeposit(DepositStatus.RELEASE_FAILED, null);
            e.addSuppressed(releaseError);
        }
    }

    if (vehicle.getAvailability() == VehicleAvailability.RESERVED) {
        vehicle.releaseReservation();
    }

    throw e;
}






} finally {
    coordinationLock.unlock();
}
}
    /**
     * Keep ACTIVE until confirmed compatible locking. Return Optional.empty on completion or Optional<Station> for nearest usable redirection (station-ID ties). No available station or failed lock raises a state error. Release the hold at confirmed end even if later fare collection fails.
     * @param tripId required input; validate malformed values before mutation
     * @param stationId required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<Station> returnVehicle(UUID tripId, String stationId) {

    if (tripId == null || stationId == null || stationId.isBlank()) {
        throw new IllegalArgumentException("Invalid trip or station ID");
    }

    coordinationLock.lock();

    try {
        Trip trip = findTrip(tripId)
            .orElseThrow(() -> new NoSuchElementException("Trip not found"));

        Station station = stations.findStation(stationId)
            .orElseThrow(() -> new NoSuchElementException("Station not found"));

if (trip.getStatus() == TripStatus.ENDED) {
    return Optional.empty();
}

if (trip.getStatus() != TripStatus.ACTIVE) {
    throw new IllegalStateException("Trip is not active");
}
        Vehicle vehicle = trip.getVehicle();

        Optional<DockingPoint> availableDock =
            station.findFreeDock(vehicle.getType());

        if (availableDock.isEmpty()) {
            Optional<Station> nearest =
                stations.nearestAvailable(stationId, vehicle.getType());

            if (nearest.isEmpty()) {
                throw new IllegalStateException("No available return station");
            }

            return nearest;
        }

        DockingPoint dock = availableDock.get();

        if (!docking.confirmLocked(dock.getId(), vehicle.getId())) {
            throw new IllegalStateException("Vehicle locking failed");
        }

        dock.attach(vehicle, true);
        trip.endAt(clock.instant(), dock.getId());
        vehicle.markDocked();

        payments.releaseDeposit(trip);

        return Optional.empty();

    } finally {
        coordinationLock.unlock();
    }
}

    /**
     * Require ended trip; charge/release and deliver receipt using stable attempt keys. Never repeat successful operations.
     * @param tripId required input; validate malformed values before mutation
     * @param channel required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public PaymentResult settleTrip(UUID tripId, ReceiptChannel channel) {

    if (tripId == null || channel == null) {
        throw new IllegalArgumentException("Invalid trip or receipt channel");
    }

    coordinationLock.lock();

    try {
        Trip trip = findTrip(tripId)
            .orElseThrow(() -> new NoSuchElementException("Trip not found"));

        if (trip.getStatus() != TripStatus.ENDED) {
            throw new IllegalStateException("Trip has not ended");
        }

        if (trip.getFare() == null) {
            FareBreakdown fare = pricing.calculateFare(trip);
            trip.recordFare(fare);
        }


if (trip.getPaymentStatus() == PaymentStatus.PAID) {
    payments.sendReceipt(trip, channel);
    return new PaymentResult(true, trip.getPaymentReference(), null, null);
}


        PaymentResult result = payments.chargeFare(trip, trip.getFare());

        if (result.success()) {
            payments.sendReceipt(trip, channel);
        }

        return result;

    } finally {
        coordinationLock.unlock();
    }
}

    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking mutable stores.
     * @param id required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws UnsupportedOperationException while this scaffold remains unimplemented
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
public Optional<Trip> findTrip(UUID id) {

    if (id == null) {
        throw new IllegalArgumentException("Trip ID cannot be null");
    }

    return Optional.ofNullable(trips.get(id));
}
    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public RiderService getRiders() {
        return riders;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public StationService getStations() {
        return stations;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public PricingService getPricing() {
        return pricing;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public PaymentService getPayments() {
        return payments;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public DockingGateway getDocking() {
        return docking;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public DeploymentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Clock getClock() {
        return clock;
    }
}
