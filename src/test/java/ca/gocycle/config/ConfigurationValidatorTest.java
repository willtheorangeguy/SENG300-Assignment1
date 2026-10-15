package ca.gocycle.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ConfigurationValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ConfigurationValidator validator = new ConfigurationValidator();

    /** Reads the example file, lets the test change it, and returns the resulting configuration. */
    private static DeploymentConfiguration changed(Consumer<ObjectNode> change) throws Exception {
        ObjectNode tree = (ObjectNode) JSON.readTree(Path.of("config/gocycle.example.json").toFile());
        change.accept(tree);
        return JSON.treeToValue(tree, DeploymentConfiguration.class);
    }

    private List<String> errorsAfter(Consumer<ObjectNode> change) throws Exception {
        return validator.validate(changed(change));
    }

    private static void assertMentions(List<String> errors, String fieldPath) {
        assertTrue(errors.stream().anyMatch(e -> e.startsWith(fieldPath + ":")),
                "expected an error for " + fieldPath + " but got " + errors);
    }

    @Test
    void acceptsTheExampleConfiguration() throws Exception {
        assertEquals(List.of(), errorsAfter(tree -> { }));
    }

    @Test
    void nullConfigurationIsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test
    void rejectsNegativeAndZeroAmounts() throws Exception {
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("unlockFee", "-1.00")), "pricing.unlockFee");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("deposit", "0.00")), "pricing.deposit");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("surcharge", "0.00")), "pricing.surcharge");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("membership")).put("price", "0.00")), "membership.price");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.at("/pricing/rates")).put("CASUAL", "0.00")), "pricing.rates.CASUAL");
    }

    @Test
    void zeroUnlockFeeIsAllowed() throws Exception {
        assertEquals(List.of(), errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("unlockFee", "0.00")));
    }

    @Test
    void rejectsAmountsWithoutTwoDecimalPlaces() throws Exception {
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("deposit", "50")), "pricing.deposit");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("deposit", "50.0")), "pricing.deposit");
    }

    @Test
    void reducedRatesMustBeLowerThanCasual() throws Exception {
        assertMentions(errorsAfter(t -> ((ObjectNode) t.at("/pricing/rates")).put("MEMBER", "0.50")), "pricing.rates.MEMBER");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.at("/pricing/rates")).put("STUDENT", "0.60")), "pricing.rates.STUDENT");
    }

    @Test
    void ratesMustListExactlyTheKnownCategories() throws Exception {
        assertMentions(errorsAfter(t -> ((ObjectNode) t.at("/pricing/rates")).remove("STUDENT")), "pricing.rates.STUDENT");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.at("/pricing/rates")).put("VIP", "0.10")), "pricing.rates.VIP");
    }

    @Test
    void rejectsValuesThatContradictTheAssumptions() throws Exception {
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("samePricesForAllVehicles", false)),
                "pricing.samePricesForAllVehicles");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("pricing")).put("minimumTripCharge", "1.00")),
                "pricing.minimumTripCharge");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("students")).put("monthlyMembershipAllowed", true)),
                "students.monthlyMembershipAllowed");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("students")).put("ageRestriction", 18)), "students.ageRestriction");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("membership")).put("durationDays", 31)), "membership.durationDays");
        assertMentions(errorsAfter(t -> ((ArrayNode) t.at("/riders/registrationFields")).add("BIRTH_DATE")),
                "riders.registrationFields");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("riders")).put("maximumActiveTrips", 2)), "riders.maximumActiveTrips");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("trips")).put("removalTimeoutMinutes", 3)), "trips.removalTimeoutMinutes");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("trips")).put("maximumDurationHours", 24)), "trips.maximumDurationHours");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("stations")).put("count", 6)), "stations.count");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("faults")).put("reportedBrokenRentable", true)),
                "faults.reportedBrokenRentable");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("operators")).put("batteryOverrideAllowed", false)),
                "operators.batteryOverrideAllowed");
        assertMentions(errorsAfter(t -> ((ArrayNode) t.at("/interfaces/supportedLanguages")).remove(1)),
                "interfaces.supportedLanguages");
        assertMentions(errorsAfter(t -> ((ObjectNode) t.get("receipts")).putArray("destinations").add("EMAIL")),
                "receipts.destinations");
    }

    @Test
    void reportsEveryProblemInOnePass() throws Exception {
        List<String> errors = errorsAfter(t -> {
            ((ObjectNode) t.get("pricing")).put("deposit", "0.00");
            ((ObjectNode) t.get("trips")).put("removalTimeoutMinutes", 3);
            ((ObjectNode) t.get("operators")).put("faultOverrideAllowed", false);
        });
        assertEquals(3, errors.size(), errors.toString());
    }

    @Test
    void missingSectionIsReportedByName() throws Exception {
        DeploymentConfiguration example = changed(t -> { });
        var withoutReceipts = new DeploymentConfiguration(example.getPricing(), example.getStudents(), example.getMembership(),
                example.getRiders(), example.getTrips(), example.getStations(), example.getFaults(), example.getOperators(),
                example.getInterfaces(), null);
        assertMentions(validator.validate(withoutReceipts), "receipts");
    }

    @Test
    void returnedListCannotBeModified() throws Exception {
        List<String> errors = errorsAfter(t -> { });
        assertThrows(UnsupportedOperationException.class, () -> errors.add("x"));
    }
}
