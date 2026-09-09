package org.encoway.migration.target.postgres;

import org.encoway.migration.model.City;
import org.encoway.migration.model.MigrationData;
import org.encoway.migration.model.MigrationRejection;
import org.encoway.migration.model.Person;
import org.encoway.migration.source.transfer.TransferPackageProcessor;
import org.encoway.migration.source.transfer.TransferPackageProcessor.PendingHobby;
import org.encoway.migration.source.transfer.TransferPackageProcessor.PendingProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrates assembled {@link MigrationData} into PostgreSQL: recreates the schema, imports the
 * data, applies the {@code letsmeet-transfer-v3} delivery package record-by-record (each
 * candidate is isolated with a savepoint, so one rejected record cannot block the rest of the
 * same delivery), records every rejection and (re)creates the reporting views, all within a
 * single transaction.
 */
public class DatabaseMigrator {

    private final SchemaDefinition schemaDefinition = new SchemaDefinition();
    private final MigrationViews migrationViews = new MigrationViews();
    private final ReferenceDataWriter referenceDataWriter = new ReferenceDataWriter();
    private final PersonWriter personWriter = new PersonWriter();
    private final HobbyWriter hobbyWriter = new HobbyWriter();
    private final InterestWriter interestWriter = new InterestWriter();
    private final SocialActivityWriter socialActivityWriter = new SocialActivityWriter();
    private final RejectionWriter rejectionWriter = new RejectionWriter();
    private final TransferPackageProcessor transferPackageProcessor;

    public DatabaseMigrator(String recordsPathPrefix) {
        this.transferPackageProcessor = new TransferPackageProcessor(recordsPathPrefix);
    }

    public void migrate(MigrationData migrationData) {
        try (Connection connection = DatabaseConfig.openConnection()) {
            migrateWithinTransaction(connection, migrationData);
        } catch (SQLException exception) {
            throw new RuntimeException("Could not migrate the LetsMeet database.", exception);
        }
    }

    private void migrateWithinTransaction(Connection connection, MigrationData migrationData) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            resetSchema(connection);
            importData(connection, migrationData);
            applyTransferPackage(connection, migrationData);
            migrationViews.createMigrationViews(connection);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollback(connection, exception);
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void resetSchema(Connection connection) throws SQLException {
        migrationViews.dropMigrationViews(connection);
        schemaDefinition.dropSchema(connection);
        schemaDefinition.createSchema(connection);
    }

    private void importData(Connection connection, MigrationData migrationData) throws SQLException {
        referenceDataWriter.insertCities(connection, migrationData.cities());
        referenceDataWriter.insertGenders(connection, migrationData.genders());
        personWriter.insertPeople(connection, migrationData.people());
        hobbyWriter.insertHobbies(connection, migrationData.hobbies());
        interestWriter.insertPersonInterests(connection, migrationData.personInterests());
        interestWriter.insertRawInterests(connection, migrationData.rawInterests());
        socialActivityWriter.insertPersonLikes(connection, migrationData.personLikes());
        socialActivityWriter.insertPersonMessages(connection, migrationData.personMessages());
    }

    /**
     * Applies the transfer package on top of the already-imported data: every rejection found
     * while reading/validating the package is kept as-is, and every pending hobby/profile is
     * attempted individually so that a database-level fault (e.g. a real duplicate) becomes an
     * additional rejection instead of aborting the whole import.
     */
    private void applyTransferPackage(Connection connection, MigrationData migrationData) throws SQLException {
        TransferPackageProcessor.Result result = transferPackageProcessor.process(migrationData);
        List<MigrationRejection> rejections = new ArrayList<>(result.rejections());

        for (PendingProfile pendingProfile : result.pendingProfiles()) {
            rejections.addAll(applyPendingProfile(connection, pendingProfile));
        }
        for (PendingHobby pendingHobby : result.pendingHobbies()) {
            rejections.addAll(applyPendingHobby(connection, pendingHobby));
        }

        rejectionWriter.insertRejections(connection, rejections);
    }

    private List<MigrationRejection> applyPendingProfile(Connection connection, PendingProfile pendingProfile)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            if (pendingProfile.newCity()) {
                insertCity(connection, pendingProfile.city());
            }
            insertPerson(connection, pendingProfile.person());
            return List.of();
        } catch (SQLException exception) {
            connection.rollback(savepoint);
            return List.of(new MigrationRejection(pendingProfile.source(), pendingProfile.sourceRef(),
                    "Profile could not be inserted: " + exception.getMessage()));
        }
    }

    private List<MigrationRejection> applyPendingHobby(Connection connection, PendingHobby pendingHobby)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            hobbyWriter.insertHobby(connection, pendingHobby.toHobby());
            return List.of();
        } catch (SQLException exception) {
            connection.rollback(savepoint);
            return List.of(new MigrationRejection(pendingHobby.source(), pendingHobby.sourceRef(),
                    "Hobby could not be inserted: " + exception.getMessage()));
        }
    }

    private void insertCity(Connection connection, City city) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO %s (city_id, zip_code, city_name) VALUES (?, ?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_CITY))) {
            statement.setInt(1, city.cityId());
            statement.setString(2, city.zipCode());
            statement.setString(3, city.cityName());
            statement.executeUpdate();
        }
    }

    private void insertPerson(Connection connection, Person person) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s (
                    person_id, last_name, first_name, street, street_number, city_id, phone_number,
                    email, gender_id, birth_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(DatabaseObjectNames.TABLE_PERSON))) {
            statement.setInt(1, person.personId());
            statement.setString(2, person.lastName());
            statement.setString(3, person.firstName());
            statement.setString(4, person.street());
            statement.setString(5, person.streetNumber());
            statement.setInt(6, person.cityId());
            statement.setString(7, person.phoneNumber());
            statement.setString(8, person.email());
            statement.setInt(9, person.genderId());
            statement.setObject(10, person.birthDate());
            statement.executeUpdate();
        }
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
