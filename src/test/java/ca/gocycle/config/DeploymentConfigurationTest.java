package ca.gocycle.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentConfigurationTest {

    private static DeploymentConfiguration example() throws Exception {
        return new ObjectMapper().readValue(Path.of("config/gocycle.example.json").toFile(), DeploymentConfiguration.class);
    }

    @Test
    void exposesTheTeamAssumptionsFromTheExampleFile() throws Exception {
        DeploymentConfiguration c = example();
        assertEquals(new BigDecimal("5.00"), c.getPricing().getUnlockFee());
        assertEquals(new BigDecimal("0.50"), c.getPricing().getRates().get("CASUAL"));
        assertEquals(new BigDecimal("0.20"), c.getPricing().getRates().get("MEMBER"));
        assertEquals(new BigDecimal("50.00"), c.getPricing().getDeposit());
        assertEquals(new BigDecimal("15.00"), c.getPricing().getSurcharge());
        assertNull(c.getPricing().getMinimumTripCharge());
        assertEquals("ucalgary.ca", c.getStudents().emailDomain());
        assertFalse(c.getStudents().monthlyMembershipAllowed());
        assertEquals(new BigDecimal("15.00"), c.getMembership().price());
        assertEquals(30, c.getMembership().durationDays());
        assertEquals(1, c.getRiders().maximumActiveTrips());
        assertEquals(12, c.getTrips().maximumDurationHours());
        assertEquals(List.of(3, 2), c.getStations().stationsPerZone());
        assertEquals(5, c.getStations().dockingPointsPerStation());
        assertEquals("DISCARD", c.getFaults().duplicateReports());
        assertTrue(c.getOperators().faultOverrideAllowed());
        assertEquals(List.of("en", "fr"), c.getInterfaces().supportedLanguages());
        assertEquals(List.of("EMAIL", "APP"), c.getReceipts().destinations());
    }

    @Test
    void listsInsideSettingsAreImmutableSnapshots() throws Exception {
        DeploymentConfiguration c = example();
        assertThrows(UnsupportedOperationException.class, () -> c.getInterfaces().supportedLanguages().add("de"));
        assertThrows(UnsupportedOperationException.class, () -> c.getStations().stationsPerZone().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.getPricing().getRates().put("VIP", BigDecimal.ONE));
    }

    @Test
    void changingTheListPassedInDoesNotChangeTheSettings() {
        List<String> languages = new ArrayList<>(List.of("en", "fr"));
        var rules = new DeploymentConfiguration.InterfaceRules(languages, "en", "SCREEN_BUTTON");
        languages.add("de");
        assertEquals(List.of("en", "fr"), rules.supportedLanguages());
    }

    @Test
    void settingsWithSameValuesAreEqual() {
        assertEquals(new DeploymentConfiguration.OperatorRules(true, true, true),
                new DeploymentConfiguration.OperatorRules(true, true, true));
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        ObjectMapper json = new ObjectMapper();
        DeploymentConfiguration original = example();
        DeploymentConfiguration copy = json.readValue(json.writeValueAsString(original), DeploymentConfiguration.class);
        assertEquals(original.getPricing().getUnlockFee(), copy.getPricing().getUnlockFee());
        assertEquals(original.getStations(), copy.getStations());
        assertEquals(original.getReceipts(), copy.getReceipts());
    }
}
