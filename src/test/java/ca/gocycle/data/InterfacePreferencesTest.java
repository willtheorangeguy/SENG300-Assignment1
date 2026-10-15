package ca.gocycle.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InterfacePreferencesTest {

    @Test
    void keepsLanguageAndAudioChoice() {
        var preferences = new InterfacePreferences("fr", true);
        assertEquals("fr", preferences.language());
        assertTrue(preferences.audioEnabled());
        assertFalse(new InterfacePreferences("en", false).audioEnabled());
    }

    @Test
    void recordsWithSameValuesAreEqual() {
        assertEquals(new InterfacePreferences("fr", true), new InterfacePreferences("fr", true));
    }

    @Test
    void rejectsMissingLanguage() {
        assertThrows(IllegalArgumentException.class, () -> new InterfacePreferences(null, false));
        assertThrows(IllegalArgumentException.class, () -> new InterfacePreferences("  ", false));
    }

    @Test
    void doesNotDecideWhichLanguagesAreSupported() {
        // Supported languages come from the deployment configuration and are checked by the service layer.
        assertEquals("unsupported", new InterfacePreferences("unsupported", false).language());
    }
}
