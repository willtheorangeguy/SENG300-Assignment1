package ca.gocycle.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a loaded {@link DeploymentConfiguration} against the team's agreed assumptions
 * (see {@code config/gocycle.schema.json} and the assumptions table in the report).
 *
 * <p>Every problem is reported as one line of the form {@code "<field path>: <what is wrong>"},
 * for example {@code "pricing.deposit: must be greater than zero"}. All problems are collected,
 * so a user can fix everything in one pass. An empty list means the configuration is valid.
 *
 * <p>This class only reads the configuration; it never changes it.
 */
public class ConfigurationValidator {

    private static final Set<String> RATE_CATEGORIES = Set.of("CASUAL", "MEMBER", "STUDENT");

    public ConfigurationValidator() {
    }

    /**
     * @param value the configuration to check
     * @return one message per problem, each starting with the field path; empty if the configuration is valid
     * @throws IllegalArgumentException if {@code value} is null
     */
    public List<String> validate(DeploymentConfiguration value) {
        if (value == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        List<String> errors = new ArrayList<>();
        validatePricing(value.getPricing(), errors);
        validateStudents(value.getStudents(), errors);
        validateMembership(value.getMembership(), errors);
        validateRiders(value.getRiders(), errors);
        validateTrips(value.getTrips(), errors);
        validateStations(value.getStations(), errors);
        validateFaults(value.getFaults(), errors);
        validateOperators(value.getOperators(), errors);
        validateInterfaces(value.getInterfaces(), errors);
        validateReceipts(value.getReceipts(), errors);
        return List.copyOf(errors);
    }

    // ---------------------------------------------------------------- sections

    private void validatePricing(PricingConfiguration p, List<String> errors) {
        if (p == null) {
            errors.add("pricing: section is missing");
            return;
        }
        checkMoney("pricing.unlockFee", p.getUnlockFee(), false, errors);
        checkMoney("pricing.deposit", p.getDeposit(), true, errors);
        checkMoney("pricing.surcharge", p.getSurcharge(), true, errors);
        checkRates(p.getRates(), errors);
        checkEquals("pricing.samePricesForAllVehicles", p.getSamePricesForAllVehicles(), true, errors);
        checkEquals("pricing.billingUnit", p.getBillingUnit(), "MINUTE", errors);
        checkEquals("pricing.rounding", p.getRounding(), "UP", errors);
        checkEquals("pricing.minimumTripCharge", p.getMinimumTripCharge(), null, errors);
        checkEquals("pricing.studentDiscountAppliesTo", p.getStudentDiscountAppliesTo(), "PER_MINUTE_ONLY", errors);
    }

    private void checkRates(Map<String, BigDecimal> rates, List<String> errors) {
        if (rates == null) {
            errors.add("pricing.rates: is missing");
            return;
        }
        for (String category : RATE_CATEGORIES) {
            if (!rates.containsKey(category)) {
                errors.add("pricing.rates." + category + ": is missing");
            }
        }
        for (String key : rates.keySet()) {
            if (!RATE_CATEGORIES.contains(key)) {
                errors.add("pricing.rates." + key + ": is not a known rider category");
            }
        }
        for (String category : RATE_CATEGORIES) {
            if (rates.containsKey(category)) {
                checkMoney("pricing.rates." + category, rates.get(category), true, errors);
            }
        }
        BigDecimal casual = rates.get("CASUAL");
        for (String reduced : List.of("MEMBER", "STUDENT")) {
            BigDecimal rate = rates.get(reduced);
            if (casual != null && rate != null && rate.compareTo(casual) >= 0) {
                errors.add("pricing.rates." + reduced + ": must be lower than the CASUAL rate");
            }
        }
    }

    private void validateStudents(DeploymentConfiguration.StudentRules s, List<String> errors) {
        if (s == null) {
            errors.add("students: section is missing");
            return;
        }
        checkEquals("students.emailDomain", s.emailDomain(), "ucalgary.ca", errors);
        checkEquals("students.ageRestriction", s.ageRestriction(), null, errors);
        checkEquals("students.monthlyMembershipAllowed", s.monthlyMembershipAllowed(), false, errors);
    }

    private void validateMembership(DeploymentConfiguration.MembershipRules m, List<String> errors) {
        if (m == null) {
            errors.add("membership: section is missing");
            return;
        }
        checkMoney("membership.price", m.price(), true, errors);
        checkEquals("membership.durationDays", m.durationDays(), 30, errors);
        checkEquals("membership.startsAt", m.startsAt(), "REGISTRATION", errors);
    }

    private void validateRiders(DeploymentConfiguration.RiderRules r, List<String> errors) {
        if (r == null) {
            errors.add("riders: section is missing");
            return;
        }
        checkEquals("riders.accountRequired", r.accountRequired(), true, errors);
        checkEquals("riders.registrationFields", r.registrationFields(),
                List.of("EMAIL_OR_USERNAME", "PASSWORD", "ACCOUNT_TYPE", "PAYMENT_METHOD"), errors);
        checkEquals("riders.accountTypes", r.accountTypes(), List.of("CASUAL", "MEMBER", "STUDENT"), errors);
        checkEquals("riders.stationIdentification", r.stationIdentification(), "EMAIL_OR_USERNAME_AND_PASSWORD", errors);
        checkEquals("riders.maximumActiveTrips", r.maximumActiveTrips(), 1, errors);
    }

    private void validateTrips(DeploymentConfiguration.TripRules t, List<String> errors) {
        if (t == null) {
            errors.add("trips: section is missing");
            return;
        }
        checkEquals("trips.billableTimeStartsAt", t.billableTimeStartsAt(), "VEHICLE_UNLOCKED", errors);
        checkEquals("trips.removalTimeoutMinutes", t.removalTimeoutMinutes(), 2, errors);
        checkEquals("trips.notRemovedAction", t.notRemovedAction(), "STOP_TRIP_NO_CHARGE", errors);
        checkEquals("trips.pausingAllowed", t.pausingAllowed(), false, errors);
        checkEquals("trips.maximumDurationHours", t.maximumDurationHours(), 12, errors);
        checkEquals("trips.overdueAction", t.overdueAction(), "MARK_LOST_NOTIFY_OPERATORS_CHARGE_DEPOSIT", errors);
        checkEquals("trips.lostOrStolenAction", t.lostOrStolenAction(),
                "MARK_LOST_BLOCK_RENTAL_CHARGE_LAST_USER_DEPOSIT", errors);
        checkEquals("trips.emptyBatteryAction", t.emptyBatteryAction(),
                "DIRECT_TO_NEAREST_STATION_FOR_CHARGING", errors);
    }

    private void validateStations(DeploymentConfiguration.StationRules s, List<String> errors) {
        if (s == null) {
            errors.add("stations: section is missing");
            return;
        }
        checkEquals("stations.count", s.count(), 5, errors);
        checkEquals("stations.stationsPerZone", s.stationsPerZone(), List.of(3, 2), errors);
        checkEquals("stations.dockingPointsPerStation", s.dockingPointsPerStation(), 5, errors);
        checkEquals("stations.allDocksAcceptAllVehicleTypes", s.allDocksAcceptAllVehicleTypes(), true, errors);
    }

    private void validateFaults(DeploymentConfiguration.FaultRules f, List<String> errors) {
        if (f == null) {
            errors.add("faults: section is missing");
            return;
        }
        checkEquals("faults.outOfServiceTiming", f.outOfServiceTiming(), "AFTER_CURRENT_RIDE", errors);
        checkEquals("faults.reportedBrokenRentable", f.reportedBrokenRentable(), false, errors);
        checkEquals("faults.duplicateReports", f.duplicateReports(), "DISCARD", errors);
    }

    private void validateOperators(DeploymentConfiguration.OperatorRules o, List<String> errors) {
        if (o == null) {
            errors.add("operators: section is missing");
            return;
        }
        checkEquals("operators.manualTripClosureAllowed", o.manualTripClosureAllowed(), true, errors);
        checkEquals("operators.batteryOverrideAllowed", o.batteryOverrideAllowed(), true, errors);
        checkEquals("operators.faultOverrideAllowed", o.faultOverrideAllowed(), true, errors);
    }

    private void validateInterfaces(DeploymentConfiguration.InterfaceRules i, List<String> errors) {
        if (i == null) {
            errors.add("interfaces: section is missing");
            return;
        }
        checkEquals("interfaces.supportedLanguages", i.supportedLanguages(), List.of("en", "fr"), errors);
        checkEquals("interfaces.defaultLanguage", i.defaultLanguage(), "en", errors);
        checkEquals("interfaces.audioEnabledBy", i.audioEnabledBy(), "SCREEN_BUTTON", errors);
    }

    private void validateReceipts(DeploymentConfiguration.ReceiptRules r, List<String> errors) {
        if (r == null) {
            errors.add("receipts: section is missing");
            return;
        }
        checkEquals("receipts.destinations", r.destinations(), List.of("EMAIL", "APP"), errors);
        checkEquals("receipts.fields", r.fields(),
                List.of("UNLOCK_FEE", "DURATION", "RATE", "SURCHARGE", "DEPOSIT_RELEASE"), errors);
    }

    // ----------------------------------------------------------------- helpers

    /**
     * A money amount must be present, never negative, written with exactly two decimal places
     * (like {@code 5.00}), and, when {@code mustBePositive}, greater than zero.
     */
    private static void checkMoney(String path, BigDecimal amount, boolean mustBePositive, List<String> errors) {
        if (amount == null) {
            errors.add(path + ": is missing");
            return;
        }
        if (amount.signum() < 0) {
            errors.add(path + ": must not be negative");
        } else if (mustBePositive && amount.signum() == 0) {
            errors.add(path + ": must be greater than zero");
        }
        if (amount.scale() != 2) {
            errors.add(path + ": must have exactly two decimal places (for example 5.00)");
        }
    }

    private static void checkEquals(String path, Object actual, Object expected, List<String> errors) {
        if (!Objects.equals(actual, expected)) {
            errors.add(path + ": must be " + describe(expected) + " but was " + describe(actual));
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
