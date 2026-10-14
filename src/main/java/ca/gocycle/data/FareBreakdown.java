package ca.gocycle.data;

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


public record FareBreakdown(BigDecimal unlockCharge, long billableMinutes, BigDecimal durationCharge, BigDecimal surcharge, BigDecimal total) {
    public FareBreakdown {
        if (unlockCharge == null || durationCharge == null || surcharge == null || total == null) {
            throw new IllegalArgumentException("Fare amounts are required");
        }
        if (billableMinutes < 0 || unlockCharge.signum() < 0 || durationCharge.signum() < 0
                || surcharge.signum() < 0 || total.signum() < 0) {
            throw new IllegalArgumentException("Fare amounts and minutes cannot be negative");
        }
    }
}
