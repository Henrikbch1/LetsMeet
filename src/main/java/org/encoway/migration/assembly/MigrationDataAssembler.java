package org.encoway.migration.assembly;

import org.encoway.migration.model.MigrationData;
import org.encoway.migration.model.MongoData;
import org.encoway.migration.model.Person;
import org.encoway.migration.model.PersonLike;
import org.encoway.migration.model.PersonMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges Excel {@link MigrationData} with {@link MongoData} into the final data set that is
 * imported into PostgreSQL: person profiles are merged by lower-cased email according to the
 * configured {@link ProfileConflictPolicy}, and Mongo likes/messages are resolved to internal
 * person ids.
 */
public class MigrationDataAssembler {

    private final ProfileConflictPolicy conflictPolicy;

    public MigrationDataAssembler() {
        this(ProfileConflictPolicy.MONGO_WINS);
    }

    public MigrationDataAssembler(ProfileConflictPolicy conflictPolicy) {
        this.conflictPolicy = conflictPolicy;
    }

    public MigrationData assemble(MigrationData excelData, MongoData mongoData) {
        ProfileMerger profileMerger = new ProfileMerger(conflictPolicy, mongoData.profiles());
        RelationResolver relationResolver = new RelationResolver(excelData.people());

        List<Person> mergedPeople = new ArrayList<>(excelData.people().size());
        for (Person excelPerson : excelData.people()) {
            mergedPeople.add(profileMerger.merge(excelPerson));
        }

        List<PersonLike> personLikes = relationResolver.resolveLikes(mongoData.likes());
        List<PersonMessage> personMessages = relationResolver.resolveMessages(mongoData.messages());

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
}
