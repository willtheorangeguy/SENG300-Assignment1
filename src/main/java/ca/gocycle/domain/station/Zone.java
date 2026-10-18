package ca.gocycle.domain.station;

import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ca.gocycle.application.*;
import ca.gocycle.config.*;
import ca.gocycle.data.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.fault.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public class Zone {

    private final String id;

    private Set<PaymentMethod> allowedPaymentMethods;

    private DisplayConfiguration display;

    /**
     * Create a zone with its own payment rules and screen branding.
     * @throws IllegalArgumentException if the ID is blank, the payment methods are missing, empty or
     *         contain null, or the display configuration is missing
     */
    public Zone(String id, Set<PaymentMethod> allowedPaymentMethods, DisplayConfiguration display) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Zone ID is required");
        }
        validate(allowedPaymentMethods, display);
        this.id = id;
        this.allowedPaymentMethods = new LinkedHashSet<>(allowedPaymentMethods);
        this.display = display;
    }

    /**
     * Return whether the zone permits the selected method; empty permitted lists are invalid configuration.
     * @param method payment method the rider wants to use
     * @return whether this zone accepts the method
     * @throws IllegalArgumentException if the method is missing
     */
    public synchronized boolean permits(PaymentMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        return allowedPaymentMethods.contains(method);
    }

    /**
     * Replace the zone's payment methods and branding together. Both values are validated before
     * anything changes, so a rejected update leaves the previous configuration in place. Trips that
     * already started keep the methods they captured at the start.
     * @param methods new non-empty set of accepted payment methods
     * @param display new screen colours and logo
     * @throws IllegalArgumentException if the methods are missing, empty or contain null, or the display is missing
     */
    public synchronized void updateConfiguration(Set<PaymentMethod> methods, DisplayConfiguration display) {
        validate(methods, display);
        this.allowedPaymentMethods = new LinkedHashSet<>(methods);
        this.display = display;
    }

    private static void validate(Set<PaymentMethod> methods, DisplayConfiguration display) {
        if (methods == null || methods.isEmpty()) {
            throw new IllegalArgumentException("A zone must allow at least one payment method");
        }
        for (PaymentMethod method : methods) {
            if (method == null) {
                throw new IllegalArgumentException("Allowed payment methods must not contain null");
            }
        }
        if (display == null) {
            throw new IllegalArgumentException("Zone display configuration is required");
        }
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getId() {
        return id;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized Set<PaymentMethod> getAllowedPaymentMethods() {
        return Set.copyOf(allowedPaymentMethods);
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized DisplayConfiguration getDisplay() {
        return display;
    }
}
