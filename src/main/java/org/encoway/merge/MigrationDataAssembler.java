package org.encoway.merge;

import org.encoway.model.MigrationData;
import org.encoway.model.MongoData;
import org.encoway.model.MongoLike;
import org.encoway.model.MongoMessage;
import org.encoway.model.MongoProfile;
import org.encoway.model.Person;
import org.encoway.model.PersonLike;
import org.encoway.model.PersonMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Merges Excel {@link MigrationData} with {@link MongoData} into the final data set that is
 * imported into PostgreSQL: person profiles are merged by lower-cased email according to the
 * configured {@link ProfileConflictPolicy}, and Mongo likes/messages are resolved to internal
 * person ids.
 */
public class MigrationDataAssembler {

    private static final Logger LOGGER = Logger.getLogger(MigrationDataAssembler.class.getName());

    private final ProfileConflictPolicy conflictPolicy;

    public MigrationDataAssembler() {
        this(ProfileConflictPolicy.MONGO_WINS);
    }

    public MigrationDataAssembler(ProfileConflictPolicy conflictPolicy) {
        this.conflictPolicy = conflictPolicy;
    }

    public MigrationData assemble(MigrationData excelData, MongoData mongoData) {
        Map<String, MongoProfile> mongoProfilesByLowerEmail = indexMongoProfiles(mongoData.profiles());
        Map<String, Integer> personIdByLowerEmail = new HashMap<>();
        List<Person> mergedPeople = new ArrayList<>(excelData.people().size());

        for (Person excelPerson : excelData.people()) {
            String lowerEmail = excelPerson.email().toLowerCase(Locale.ROOT);
            if (personIdByLowerEmail.putIfAbsent(lowerEmail, excelPerson.personId()) != null) {
                throw new IllegalStateException(
                        "Duplicate case-insensitive person identity detected in Excel data for person id "
                                + excelPerson.personId() + ".");
            }
            mergedPeople.add(mergeProfile(excelPerson, mongoProfilesByLowerEmail.get(lowerEmail)));
        }

        List<PersonLike> personLikes = resolveLikes(mongoData.likes(), personIdByLowerEmail);
        List<PersonMessage> personMessages = resolveMessages(mongoData.messages(), personIdByLowerEmail);

        return new MigrationData(
                excelData.cities(),
                excelData.genders(),
                mergedPeople,
                excelData.hobbies(),
                excelData.personInterests(),
                excelData.rawInterests(),
                personLikes,
                personMessages
        );
    }

    private Map<String, MongoProfile> indexMongoProfiles(List<MongoProfile> profiles) {
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

    private Person mergeProfile(Person excelPerson, MongoProfile mongoProfile) {
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

    private List<PersonLike> resolveLikes(List<MongoLike> mongoLikes, Map<String, Integer> personIdByLowerEmail) {
        List<PersonLike> personLikes = new ArrayList<>(mongoLikes.size());
        int likeId = 1;
        for (MongoLike like : mongoLikes) {
            int likerId = resolvePersonId(like.likerEmail(), personIdByLowerEmail);
            int likedId = resolvePersonId(like.likedEmail(), personIdByLowerEmail);
            personLikes.add(new PersonLike(likeId++, likerId, likedId, like.status(), like.likedAt()));
        }
        return personLikes;
    }

    private List<PersonMessage> resolveMessages(
            List<MongoMessage> mongoMessages, Map<String, Integer> personIdByLowerEmail) {
        List<PersonMessage> personMessages = new ArrayList<>(mongoMessages.size());
        int messageId = 1;
        for (MongoMessage message : mongoMessages) {
            int senderId = resolvePersonId(message.senderEmail(), personIdByLowerEmail);
            int receiverId = resolvePersonId(message.receiverEmail(), personIdByLowerEmail);
            personMessages.add(new PersonMessage(
                    messageId++, senderId, receiverId, message.conversationId(), message.body(), message.sentAt()));
        }
        return personMessages;
    }

    private int resolvePersonId(String email, Map<String, Integer> personIdByLowerEmail) {
        Integer personId = personIdByLowerEmail.get(email.toLowerCase(Locale.ROOT));
        if (personId == null) {
            throw new IllegalStateException(
                    "Unresolved relation reference: no matching person found for the referenced email.");
        }
        return personId;
    }
}
