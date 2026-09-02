package org.encoway.migration.assembly;

import org.encoway.migration.model.MongoProfile;
import org.encoway.migration.model.Person;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Indexes Mongo profiles by lower-cased email and merges an Excel {@link Person} with the
 * matching {@link MongoProfile} according to the configured {@link ProfileConflictPolicy}.
 */
final class ProfileMerger {

    private static final Logger LOGGER = Logger.getLogger(ProfileMerger.class.getName());

    private final ProfileConflictPolicy conflictPolicy;
    private final Map<String, MongoProfile> mongoProfilesByLowerEmail;

    protected ProfileMerger(ProfileConflictPolicy conflictPolicy, List<MongoProfile> profiles) {
        this.conflictPolicy = conflictPolicy;
        this.mongoProfilesByLowerEmail = indexMongoProfiles(profiles);
    }

    private static Map<String, MongoProfile> indexMongoProfiles(List<MongoProfile> profiles) {
        Map<String, MongoProfile> byLowerEmail = new HashMap<>();
        for (MongoProfile profile : profiles) {
            String lowerEmail = profile.email().toLowerCase(Locale.ROOT);
            if (byLowerEmail.putIfAbsent(lowerEmail, profile) != null) {
                throw new IllegalStateException(
                        "Duplicate case-insensitive person identity detected in Mongo user data.");
            }
        }
        return byLowerEmail;
    }

    protected Person merge(Person excelPerson) {
        String lowerEmail = excelPerson.email().toLowerCase(Locale.ROOT);
        MongoProfile mongoProfile = mongoProfilesByLowerEmail.get(lowerEmail);
        if (mongoProfile == null) {
            return excelPerson;
        }
        boolean excelWins = switch (conflictPolicy) {
            case EXCEL_WINS -> true;
            case MONGO_WINS -> false;
        };
        String lastName = resolveField(
                excelPerson.lastName(), mongoProfile.lastName(), "last name", excelPerson.personId(), excelWins);
        String firstName = resolveField(
                excelPerson.firstName(), mongoProfile.firstName(), "first name", excelPerson.personId(), excelWins);
        String phoneNumber = resolveField(
                excelPerson.phoneNumber(), mongoProfile.phone(), "phone number", excelPerson.personId(), excelWins);
        return new Person(
                excelPerson.personId(),
                lastName,
                firstName,
                excelPerson.street(),
                excelPerson.streetNumber(),
                excelPerson.cityId(),
                phoneNumber,
                excelPerson.email(),
                excelPerson.genderId(),
                excelPerson.birthDate()
        );
    }

    /**
     * Applies the configured conflict policy: a nonblank value from the winning source
     * (Excel when {@code excelWins} is true, Mongo otherwise) is never overwritten by the
     * other source; the other source only supplements a field that is blank in the winning
     * source. Conflicting nonblank values are logged as a rule statement without exposing
     * the actual (personal) field values.
     */
    private String resolveField(
            String excelValue, String mongoValue, String fieldName, int personId, boolean excelWins) {
        String winningValue = excelWins ? excelValue : mongoValue;
        String losingValue = excelWins ? mongoValue : excelValue;
        boolean winningBlank = winningValue == null || winningValue.isBlank();
        boolean losingBlank = losingValue == null || losingValue.isBlank();
        if (winningBlank) {
            return losingBlank ? winningValue : losingValue;
        }
        if (!losingBlank && !winningValue.equals(losingValue)) {
            LOGGER.info(() -> "Conflict policy " + conflictPolicy
                    + " applied: keeping the " + (excelWins ? "Excel" : "Mongo") + " value for field '" + fieldName
                    + "' over a differing " + (excelWins ? "Mongo" : "Excel")
                    + " value (internal person id " + personId + ").");
        }
        return winningValue;
    }
}
