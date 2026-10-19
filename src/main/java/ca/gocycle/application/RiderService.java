package ca.gocycle.application;

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

import java.math.*;
import java.time.*;
import java.util.*;

public class RiderService {
    private final Map<UUID, Rider> riders;
    private final Map<UUID, Instant> registrationTimes = new HashMap<>();
    private final Map<UUID, Integer> membershipAttempts = new HashMap<>();
    private final PaymentService paymentService;
    private final DeploymentConfiguration configuration;
    private final Clock clock;

    /** Assign required attributes/dependencies; no business rules are implemented here. */
    public RiderService(
            PaymentService paymentService, DeploymentConfiguration configuration, Clock clock) {
        this.riders = new LinkedHashMap<>();
        this.paymentService = paymentService;
        this.configuration = configuration;
        this.clock = clock;
    }

    /**
     * Validate required account details and configured age rules; create one rider in the shared
     * rider store. Identification is simulated.
     *
     * @param name required input; validate malformed values before mutation
     * @param email required input; validate malformed values before mutation
     * @param birthDate required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized Rider register(String name, String email, LocalDate birthDate) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Invalid name");
        }

        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Invalid email");
        }

        if (birthDate == null || birthDate.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Invalid birth date");
        }

        if (riders.values().stream().anyMatch(rider -> rider.getEmail().equalsIgnoreCase(email))) {
            throw new IllegalArgumentException("An account already uses this email");
        }

        UUID id = UUID.randomUUID();

        Rider rider =
                new Rider(
                        id,
                        name,
                        email,
                        birthDate,
                        null,
                        null,
                        null,
                        new InterfacePreferences(
                                configuration.getInterfaces().defaultLanguage(), false));

        riders.put(id, rider);
        registrationTimes.put(id, clock.instant());

        return rider;
    }

    /**
     * Require a known rider with a ucalgary.ca email without an age restriction; reject member
     * overlap.
     *
     * @param riderId required input; validate malformed values before mutation
     * @param institutionId required input; validate malformed values before mutation
     * @param partTime required input; validate malformed values before mutation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized void verifyStudent(UUID riderId, String institutionId, boolean partTime) {
        if (riderId == null) {
            throw new IllegalArgumentException("Rider ID cannot be null");
        }

        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("Invalid institution ID");
        }
        Rider rider = riders.get(riderId);

        if (rider == null) {
            throw new NoSuchElementException("Rider not found");
        }
        String domain = configuration.getStudents().emailDomain();

        if (!rider.getEmail()
                .toLowerCase(Locale.ROOT)
                .endsWith("@" + domain.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Student email must be from " + domain);
        }

        if (rider.getMembership() != null && rider.hasActiveMembership(clock.instant())) {
            throw new IllegalStateException("Rider already has an active membership");
        }
        rider.setStudentVerification(institutionId, clock.instant().plus(Duration.ofDays(365)));
    }

    /**
     * Reject student overlap; activate only after successful permitted payment, for 30 days from
     * registration.
     *
     * @param riderId required input; validate malformed values before mutation
     * @param method required input; validate malformed values before mutation
     * @param zone required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized Membership activateMembership(
            UUID riderId, PaymentMethod method, Zone zone) {
        if (riderId == null) {
            throw new IllegalArgumentException("Rider ID cannot be null");
        }

        if (method == null) {
            throw new IllegalArgumentException("Payment method cannot be null");
        }

        if (zone == null) {
            throw new IllegalArgumentException("Zone cannot be null");
        }
        Rider rider = riders.get(riderId);

        if (rider == null) {
            throw new NoSuchElementException("Rider not found");
        }
        if (rider.hasValidStudentEvidence(clock.instant())) {
            throw new IllegalStateException("Students cannot activate a monthly membership");
        }
        if (rider.hasActiveMembership(clock.instant())) {
            throw new IllegalStateException("Rider already has an active membership");
        }

        Instant start = registrationTimes.get(riderId);
        if (rider.getMembership() != null) {
            // Renewal starts when requested; do not sell an already-expired interval.
            start = clock.instant();
        }
        Instant end = start.plus(Duration.ofDays(configuration.getMembership().durationDays()));
        if (!clock.instant().isBefore(end)) {
            throw new IllegalStateException("Registration membership period has expired");
        }

        PaymentResult result =
                paymentService.payMembership(
                        rider,
                        method,
                        zone,
                        "membership-"
                                + riderId
                                + "-attempt-"
                                + membershipAttempts.getOrDefault(riderId, 1));

        if (!result.success()) {
            membershipAttempts.merge(riderId, 2, (previous, ignored) -> previous + 1);
            throw new IllegalStateException("Membership payment failed");
        }

        Membership membership = new Membership(start, end, result.reference());

        rider.setMembership(membership);
        membershipAttempts.merge(riderId, 2, (previous, ignored) -> previous + 1);

        return membership;
    }

    /**
     * Require a supported language; save language/audio preferences for the identified rider.
     *
     * @param riderId required input; validate malformed values before mutation
     * @param value required input; validate malformed values before mutation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized void updatePreferences(UUID riderId, InterfacePreferences value) {
        if (riderId == null) {
            throw new IllegalArgumentException("Rider ID cannot be null");
        }

        if (value == null) {
            throw new IllegalArgumentException("Preferences cannot be null");
        }
        Rider rider = riders.get(riderId);

        if (rider == null) {
            throw new NoSuchElementException("Rider not found");
        }
        String language = value.language();

        if (language == null
                || !configuration.getInterfaces().supportedLanguages().contains(language)) {
            throw new IllegalArgumentException("Unsupported language");
        }
        rider.updatePreferences(value);
    }

    /**
     * Require a known identifier; return the matching domain object/snapshot without leaking
     * mutable stores.
     *
     * @param id required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed
     *     implementation
     */
    public synchronized Optional<Rider> findRider(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Rider ID cannot be null");
        }

        return Optional.ofNullable(riders.get(id));
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public PaymentService getPaymentService() {
        return paymentService;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public DeploymentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     *
     * @return the recorded attribute
     */
    public Clock getClock() {
        return clock;
    }
}
