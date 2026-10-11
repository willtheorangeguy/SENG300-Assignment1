package ca.gocycle.pricing;

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
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.simulation.*;


public interface PricingPolicy {

    /**
     * Use configured eligibility and evidence at the supplied time. Reject student/member overlap according to report assumptions.
     * @param rider required input; validate malformed values before mutation
     * @param at required input; validate malformed values before mutation
     * @param config required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public boolean supports(Rider rider, Instant at, DeploymentConfiguration config);

    /**
     * Return configured category/type terms; missing terms are configuration errors, with no hard-coded fallback.
     * @param type required input; validate malformed values before mutation
     * @param config required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public PricingConfiguration.FareTerms termsFor(VehicleType type, PricingConfiguration config);
}
