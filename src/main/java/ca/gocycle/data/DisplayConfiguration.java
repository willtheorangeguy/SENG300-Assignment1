package ca.gocycle.data;

import java.util.regex.Pattern;

/**
 * Branding shown on a zone's screens: two colours and a logo file.
 *
 * <p>Colours must be written as {@code #RRGGBB} (for example {@code #176B56}). The logo path is
 * kept as supplied (for example {@code assets/central-logo.svg}); this record does not check that
 * the file exists, because the path is resolved by whoever displays it.
 *
 * @param primaryColour   main brand colour, {@code #RRGGBB}
 * @param secondaryColour secondary brand colour, {@code #RRGGBB}
 * @param logoPath        non-blank path to the zone logo
 * @throws IllegalArgumentException if a colour is null or not {@code #RRGGBB}, or the logo path is null or blank
 */
public record DisplayConfiguration(String primaryColour, String secondaryColour, String logoPath) {

    private static final Pattern HEX_COLOUR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public DisplayConfiguration {
        requireColour("primaryColour", primaryColour);
        requireColour("secondaryColour", secondaryColour);
        if (logoPath == null || logoPath.isBlank()) {
            throw new IllegalArgumentException("logoPath must not be blank");
        }
    }

    private static void requireColour(String field, String value) {
        if (value == null || !HEX_COLOUR.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a colour written as #RRGGBB but was: " + value);
        }
    }
}
