package ca.gocycle.acceptance;

import ca.gocycle.config.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAcceptanceTest {
    @Test @Tag("pending-implementation") void loaderReturnsTypedValidatedExample() {
        var loaded=new ConfigurationLoader(new ConfigurationValidator()).load(Path.of("config/gocycle.example.json"));
        assertEquals(new java.math.BigDecimal("5.00"),loaded.getPricing().getUnlockFee());assertEquals(5,loaded.getStations().count());
    }
    @Test @Tag("pending-implementation") void selectedMissingFileDoesNotFallBack() {
        Path path=Path.of("config/no-such-deployment.json");
        var error=assertThrows(IllegalArgumentException.class,()->new ConfigurationLoader(new ConfigurationValidator()).load(path));
        assertTrue(error.getMessage().contains("no-such-deployment"));
    }
    @ParameterizedTest @Tag("pending-implementation") @ValueSource(strings={"bad-money","nonpositive-deposit","missing-rate","equal-reduced-rate","per-vehicle-price","student-membership","age-restriction","membership-duration","registration-field","removal-timeout","maximum-duration","operator-override","languages","unknown-section"})
    void invalidProfilesRejectWithFieldPaths(String invalid) throws Exception {
        ObjectMapper json=new ObjectMapper();ObjectNode d=(ObjectNode)json.readTree(Path.of("config/gocycle.example.json").toFile());
        String expected;
        switch(invalid) {
            case "bad-money": ((ObjectNode)d.get("pricing")).put("unlockFee","oops");expected="unlockFee";break;
            case "nonpositive-deposit": ((ObjectNode)d.get("pricing")).put("deposit","0.00");expected="deposit";break;
            case "missing-rate": ((ObjectNode)d.at("/pricing/rates")).remove("STUDENT");expected="rates";break;
            case "equal-reduced-rate": ((ObjectNode)d.at("/pricing/rates")).put("MEMBER","0.50");expected="rates";break;
            case "per-vehicle-price": ((ObjectNode)d.get("pricing")).put("samePricesForAllVehicles",false);expected="samePricesForAllVehicles";break;
            case "student-membership": ((ObjectNode)d.get("students")).put("monthlyMembershipAllowed",true);expected="monthlyMembershipAllowed";break;
            case "age-restriction": ((ObjectNode)d.get("students")).put("ageRestriction",18);expected="ageRestriction";break;
            case "membership-duration": ((ObjectNode)d.get("membership")).put("durationDays",31);expected="durationDays";break;
            case "registration-field": ((ArrayNode)d.at("/riders/registrationFields")).add("BIRTH_DATE");expected="registrationFields";break;
            case "removal-timeout": ((ObjectNode)d.get("trips")).put("removalTimeoutMinutes",3);expected="removalTimeoutMinutes";break;
            case "maximum-duration": ((ObjectNode)d.get("trips")).put("maximumDurationHours",24);expected="maximumDurationHours";break;
            case "operator-override": ((ObjectNode)d.get("operators")).put("batteryOverrideAllowed",false);expected="batteryOverrideAllowed";break;
            case "languages": ((ArrayNode)d.at("/interfaces/supportedLanguages")).remove(1);expected="supportedLanguages";break;
            default: d.putObject("simulation").put("paymentHoldSucceeds",true);expected="simulation";
        }
        var path=Files.createTempFile("gocycle-invalid-",".json");
        try {
            json.writeValue(path.toFile(),d);
            var error=assertThrows(IllegalArgumentException.class,()->new ConfigurationLoader(new ConfigurationValidator()).load(path));
            assertTrue(error.getMessage().contains(expected),error.getMessage());
        } finally {Files.deleteIfExists(path);}
    }
    @Test @Tag("pending-implementation") void validatorAcceptsExample() throws Exception {
        assertTrue(new ConfigurationValidator().validate(Fixtures.config()).isEmpty());
    }
    @Test @Tag("pending-implementation") void unknownKeyAndMalformedSelectedJsonReject() throws Exception {
        for(String value:new String[]{"{broken-json}","{\"unrecognized\":true}"}) {
            var path=Files.createTempFile("gocycle-malformed-",".json");
            try {Files.writeString(path,value);assertThrows(IllegalArgumentException.class,()->new ConfigurationLoader(new ConfigurationValidator()).load(path));}
            finally {Files.deleteIfExists(path);}
        }
    }
}
