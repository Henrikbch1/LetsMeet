package org.encoway.migration.target.postgres;

import org.encoway.migration.model.MigrationData;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Migrates assembled {@link MigrationData} into PostgreSQL: recreates the schema, imports the
 * data and (re)creates the reporting views, all within a single transaction.
 */
public class DatabaseMigrator {

    private final SchemaDefinition schemaDefinition = new SchemaDefinition();
    private final MigrationViews migrationViews = new MigrationViews();
    private final ReferenceDataWriter referenceDataWriter = new ReferenceDataWriter();
    private final PersonWriter personWriter = new PersonWriter();
    private final HobbyWriter hobbyWriter = new HobbyWriter();
    private final InterestWriter interestWriter = new InterestWriter();
    private final SocialActivityWriter socialActivityWriter = new SocialActivityWriter();

    public void migrate(MigrationData migrationData) {
        try (Connection connection = DatabaseConfig.openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                resetSchema(connection);
                importData(connection, migrationData);
                migrationViews.createMigrationViews(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Could not migrate the LetsMeet database.", exception);
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

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
