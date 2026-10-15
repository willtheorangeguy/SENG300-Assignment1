package ca.gocycle.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads one JSON configuration file and turns it into a validated {@link DeploymentConfiguration}.
 *
 * <p>The loader is strict on purpose:
 * <ul>
 *   <li>it reads exactly the file it is given and never falls back to built-in "demo" values;</li>
 *   <li>malformed JSON, unknown keys, duplicate keys, missing fields and wrongly typed values are
 *       rejected;</li>
 *   <li>after reading, the {@link ConfigurationValidator} checks the values, and every problem it
 *       finds is reported together.</li>
 * </ul>
 * Every failure is an {@link IllegalArgumentException} whose message names the file and the
 * offending field.
 */
public class ConfigurationLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final ConfigurationValidator validator;

    /** @param validator the validator used to check every loaded configuration */
    public ConfigurationLoader(ConfigurationValidator validator) {
        this.validator = validator;
    }

    /**
     * @param path the configuration file to read
     * @return the loaded and validated configuration
     * @throws IllegalArgumentException if the path is null, the file is missing or unreadable, the JSON is
     *                                  malformed, a key is unknown or missing, or any value is invalid
     */
    public DeploymentConfiguration load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Configuration path must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Configuration file not found: " + path);
        }

        DeploymentConfiguration configuration;
        try {
            configuration = MAPPER.readValue(path.toFile(), DeploymentConfiguration.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid configuration file " + path + ": " + describe(e), e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read configuration file " + path + ": " + e.getMessage(), e);
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Invalid configuration file " + path + ": the file is empty");
        }

        List<String> errors = validator.validate(configuration);
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Invalid configuration file ").append(path).append(':');
            for (String error : errors) {
                message.append("\n - ").append(error);
            }
            throw new IllegalArgumentException(message.toString());
        }
        return configuration;
    }

    public ConfigurationValidator getValidator() {
        return validator;
    }

    /** Builds a short message such as {@code pricing.unlockFee: Cannot deserialize ...}. */
    private static String describe(JsonProcessingException e) {
        String reason = e.getOriginalMessage();
        if (reason == null) {
            reason = e.getClass().getSimpleName();
        }
        int newline = reason.indexOf('\n');
        if (newline >= 0) {
            reason = reason.substring(0, newline);
        }
        if (e instanceof JsonMappingException mapping) {
            StringBuilder where = new StringBuilder();
            for (JsonMappingException.Reference step : mapping.getPath()) {
                if (step.getFieldName() != null) {
                    if (where.length() > 0) {
                        where.append('.');
                    }
                    where.append(step.getFieldName());
                } else if (step.getIndex() >= 0) {
                    where.append('[').append(step.getIndex()).append(']');
                }
            }
            if (e instanceof UnrecognizedPropertyException unknown && !endsWithField(where, unknown.getPropertyName())) {
                if (where.length() > 0) {
                    where.append('.');
                }
                where.append(unknown.getPropertyName());
            }
            if (where.length() > 0) {
                return where + ": " + reason;
            }
        }
        return reason;
    }

    private static boolean endsWithField(StringBuilder where, String field) {
        return field == null || where.toString().equals(field) || where.toString().endsWith("." + field);
    }
}
