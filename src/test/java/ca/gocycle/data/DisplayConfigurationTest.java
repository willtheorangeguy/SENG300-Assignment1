package ca.gocycle.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DisplayConfigurationTest {

    @Test
    void keepsValidBrandingValues() {
        var display = new DisplayConfiguration("#176B56", "#ffffff", "assets/central-logo.svg");
        assertEquals("#176B56", display.primaryColour());
        assertEquals("#ffffff", display.secondaryColour());
        assertEquals("assets/central-logo.svg", display.logoPath());
    }

    @Test
    void recordsWithSameValuesAreEqual() {
        assertEquals(new DisplayConfiguration("#000000", "#FFFFFF", "a.svg"),
                new DisplayConfiguration("#000000", "#FFFFFF", "a.svg"));
    }

    @Test
    void rejectsMalformedColours() {
        for (String bad : new String[] {null, "", "red", "176B56", "#12345", "#1234567", "#GGGGGG"}) {
            assertThrows(IllegalArgumentException.class, () -> new DisplayConfiguration(bad, "#FFFFFF", "a.svg"), "primary: " + bad);
            assertThrows(IllegalArgumentException.class, () -> new DisplayConfiguration("#000000", bad, "a.svg"), "secondary: " + bad);
        }
    }

    @Test
    void rejectsBlankLogoPath() {
        assertThrows(IllegalArgumentException.class, () -> new DisplayConfiguration("#000000", "#FFFFFF", null));
        assertThrows(IllegalArgumentException.class, () -> new DisplayConfiguration("#000000", "#FFFFFF", " "));
    }
}
