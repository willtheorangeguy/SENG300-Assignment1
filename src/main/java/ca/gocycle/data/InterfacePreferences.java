package ca.gocycle.data;

/**
 * A rider's screen preferences: which language to show and whether to read information aloud.
 *
 * <p>This record only checks that a language was given. Whether the language is one of the
 * supported languages is decided by the code that has the deployment configuration
 * (see {@code interfaces.supportedLanguages}), not here.
 *
 * @param language     language code such as {@code en} or {@code fr}; must not be blank
 * @param audioEnabled true if information should also be read aloud
 * @throws IllegalArgumentException if the language is null or blank
 */
public record InterfacePreferences(String language, boolean audioEnabled) {

    public InterfacePreferences {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language must not be blank");
        }
    }
}
