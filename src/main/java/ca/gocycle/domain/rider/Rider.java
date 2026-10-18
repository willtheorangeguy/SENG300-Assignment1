package ca.gocycle.domain.rider;

import ca.gocycle.data.InterfacePreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.IllformedLocaleException;
import java.util.UUID;

/** Account identity and eligibility; deployment-specific registration rules belong to RiderService. */
public class Rider {

    private final UUID id;

    private final String name;

    private final String email;

    private final LocalDate birthDate;
    /** Nullable until supplied/recorded; see the operation contract. */
    private String studentInstitutionId;
    /** Nullable until supplied/recorded; see the operation contract. */
  private Instant studentVerifiedUntil;
    /** Nullable until supplied/recorded; see the operation contract. */
    private Membership membership;

    private InterfacePreferences preferences;

    /**
     * Validates account values and rejects overlapping paid membership/student evidence.
     * Institution eligibility, age rules and supported languages are checked by RiderService.
     * @throws IllegalArgumentException for malformed identity, evidence or preferences
     * @throws IllegalStateException for overlapping student and paid membership eligibility
     */
    public Rider(UUID id, String name, String email, LocalDate birthDate, String studentInstitutionId, Instant studentVerifiedUntil, Membership membership, InterfacePreferences preferences) {
        if (id == null || name == null || name.isBlank() || email == null
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || birthDate == null) {
            throw new IllegalArgumentException("Rider requires an ID, name, email address and birth date");
        }
        if ((studentInstitutionId == null) != (studentVerifiedUntil == null)
                || (studentInstitutionId != null && studentInstitutionId.isBlank())) {
            throw new IllegalArgumentException("Student institution and evidence expiry must be supplied together");
        }
        validatePreferences(preferences);
        if (membership != null && membership.getPaymentReference() != null
                && studentVerifiedUntil != null && membership.getValidFrom().isBefore(studentVerifiedUntil)) {
            throw new IllegalStateException("Student eligibility cannot overlap a paid monthly membership");
        }
        this.id = id;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        this.studentInstitutionId = studentInstitutionId;
        this.studentVerifiedUntil = studentVerifiedUntil;
        this.membership = membership;
        this.preferences = preferences;
    }

    /** Evaluates eligibility at the supplied time; null times are illegal arguments. */
    public boolean hasActiveMembership(Instant at) {
        if (at == null) throw new IllegalArgumentException("Evaluation time is required");
        return membership != null && membership.isActiveAt(at);
    }

    /** Evaluates eligibility at the supplied time; null times are illegal arguments. */
    public boolean hasValidStudentEvidence(Instant at) {
        if (at == null) throw new IllegalArgumentException("Evaluation time is required");
        return studentInstitutionId != null && at.isBefore(studentVerifiedUntil);
    }

    public void setStudentVerification(String institutionId, Instant verifiedUntil) {
    if (institutionId == null || institutionId.isBlank() || verifiedUntil == null) {
        throw new IllegalArgumentException("Invalid student verification");
    }

    this.studentInstitutionId = institutionId;
    this.studentVerifiedUntil = verifiedUntil;
}

    /**
     * Stores valid preferences; RiderService must check the configured supported languages first.
     * @throws IllegalArgumentException for null preferences or a malformed language tag
     */
    public synchronized void updatePreferences(InterfacePreferences value) {
        validatePreferences(value);
        preferences = value;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public UUID getId() {
        return id;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getName() {
        return name;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getEmail() {
        return email;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public LocalDate getBirthDate() {
        return birthDate;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public String getStudentInstitutionId() {
        return studentInstitutionId;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Instant getStudentVerifiedUntil() {
        return studentVerifiedUntil;
    }

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
    if (membership == null) {
        throw new IllegalArgumentException("Membership cannot be null");
    }

    this.membership = membership;
}

    /**
     * Mechanical accessor; collections are immutable snapshots.
     * @return the recorded attribute
     */
    public synchronized InterfacePreferences getPreferences() {
        return preferences;
    }
    private static void validatePreferences(InterfacePreferences value) {
        if (value == null || value.language() == null || value.language().isBlank()) {
            throw new IllegalArgumentException("Preferences require a language tag");
        }
        try {
            new Locale.Builder().setLanguageTag(value.language()).build();
        } catch (IllformedLocaleException ex) {
            throw new IllegalArgumentException("Invalid language tag: " + value.language(), ex);
        }
    }
}
