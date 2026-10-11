package ca.gocycle.integration;

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
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;


public interface PaymentGateway {

    /**
     * Authorize the quoted amount with the selected method/currency and stable request ID; return structured provider failure.
     * @param amount required input; validate malformed values before mutation
     * @param currency required input; validate malformed values before mutation
     * @param method required input; validate malformed values before mutation
     * @param requestId required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public PaymentResult authorizeHold(BigDecimal amount, String currency, PaymentMethod method, String requestId);

    /**
     * Charge once per stable logical request; an explicit new retry may use a new attempt key.
     * @param amount required input; validate malformed values before mutation
     * @param currency required input; validate malformed values before mutation
     * @param method required input; validate malformed values before mutation
     * @param requestId required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public PaymentResult charge(BigDecimal amount, String currency, PaymentMethod method, String requestId);

    /**
     * Release the identified hold once per logical request; provider failures must remain retryable.
     * @param reference nullable only where documented
     * @param requestId required input; validate malformed values before mutation
     * @return the documented result after successful implementation
     * @throws IllegalArgumentException for malformed input in the completed implementation
     * @throws NoSuchElementException for unknown IDs in the completed implementation
     * @throws IllegalStateException for rejected business/state transitions in the completed implementation
     */
    public PaymentResult releaseHold(String reference, String requestId);
}
