package ca.gocycle.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Extra loader checks; the scenario tests live in ConfigurationAcceptanceTest. */
class ConfigurationLoaderTest {

    private static final Path EXAMPLE = Path.of("config/gocycle.example.json");
    private final ConfigurationLoader loader = new ConfigurationLoader(new ConfigurationValidator());

    private String loadFailure(String json) throws Exception {
        Path file = Files.createTempFile("gocycle-loader-", ".json");
        try {
            Files.writeString(file, json);
            return assertThrows(IllegalArgumentException.class, () -> loader.load(file)).getMessage();
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private String loadFailureAfter(Consumer<ObjectNode> change) throws Exception {
        ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(EXAMPLE.toFile());
        change.accept(tree);
        return loadFailure(tree.toString());
    }

    @Test
    void exposesItsValidator() {
        var validator = new ConfigurationValidator();
        assertSame(validator, new ConfigurationLoader(validator).getValidator());
    }

    @Test
    void loadsTheExampleFileWithExactMoneyValues() {
        DeploymentConfiguration c = loader.load(EXAMPLE);
        assertEquals(new BigDecimal("5.00"), c.getPricing().getUnlockFee());
        assertEquals(new BigDecimal("50.00"), c.getPricing().getDeposit());
        assertEquals(5, c.getStations().count());
    }

    @Test
    void nullPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
    }

    @Test
    void directoryIsNotAConfigurationFile() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(Path.of("config")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        assertTrue(loadFailure("").contains("Invalid configuration file"));
    }

    @Test
    void badMoneyNamesTheField() throws Exception {
        String message = loadFailureAfter(t -> ((ObjectNode) t.get("pricing")).put("unlockFee", "oops"));
        assertTrue(message.contains("pricing.unlockFee"), message);
    }

    @Test
    void unknownKeyNamesTheKey() throws Exception {
        String message = loadFailureAfter(t -> ((ObjectNode) t.get("pricing")).put("surgePricing", "1.00"));
        assertTrue(message.contains("surgePricing"), message);
    }

    @Test
    void missingFieldIsRejected() throws Exception {
        String message = loadFailureAfter(t -> ((ObjectNode) t.get("pricing")).remove("deposit"));
        assertTrue(message.contains("deposit"), message);
    }

    @Test
    void missingSectionIsRejected() throws Exception {
        String message = loadFailureAfter(t -> t.remove("receipts"));
        assertTrue(message.contains("receipts"), message);
    }

    @Test
    void wronglyTypedValueIsRejected() throws Exception {
        String message = loadFailureAfter(t -> ((ObjectNode) t.get("stations")).put("count", "five"));
        assertTrue(message.contains("stations.count"), message);
    }

    @Test
    void nullForAPrimitiveSettingIsRejected() throws Exception {
        String message = loadFailureAfter(t -> ((ObjectNode) t.get("trips")).putNull("pausingAllowed"));
        assertTrue(message.contains("pausingAllowed"), message);
    }

    @Test
    void duplicateKeysAreRejected() throws Exception {
        String json = Files.readString(EXAMPLE).replaceFirst("\"students\"", "\"membership\": {}, \"students\"");
        assertTrue(loadFailure(json).contains("membership"));
    }

    @Test
    void extraContentAfterTheJsonIsRejected() throws Exception {
        assertTrue(loadFailure(Files.readString(EXAMPLE) + " {}").contains("Invalid configuration file"));
    }

    @Test
    void reportsAllValidationProblemsTogether() throws Exception {
        String message = loadFailureAfter(t -> {
            ((ObjectNode) t.get("pricing")).put("deposit", "0.00");
            ((ObjectNode) t.get("trips")).put("removalTimeoutMinutes", 3);
        });
        assertTrue(message.contains("pricing.deposit"), message);
        assertTrue(message.contains("trips.removalTimeoutMinutes"), message);
    }
}
