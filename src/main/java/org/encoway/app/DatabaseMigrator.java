package org.encoway.app;

import org.encoway.config.DatabaseConfig;
import org.encoway.model.MigrationData;
import org.encoway.persistence.DataInserter;
import org.encoway.persistence.MigrationViews;
import org.encoway.persistence.SchemaDefinition;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseMigrator {

    public void migrate(MigrationData migrationData) {
        try (Connection connection = DriverManager.getConnection(
                DatabaseConfig.DATABASE_URL,
                DatabaseConfig.DATABASE_USER,
                DatabaseConfig.PASSWORD)) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                createTables(connection);
                importData(connection, migrationData);
                createMigrationUsersView(connection);
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

    public void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            new MigrationViews().dropMigrationViews(connection);
            statement.executeUpdate("DROP TABLE IF EXISTS person_message");
            statement.executeUpdate("DROP TABLE IF EXISTS person_like");
            statement.executeUpdate("DROP TABLE IF EXISTS person_interest_text");
            statement.executeUpdate("DROP TABLE IF EXISTS person_interest");
            statement.executeUpdate("DROP TABLE IF EXISTS hobby");
            statement.executeUpdate("DROP TABLE IF EXISTS person");
            statement.executeUpdate("DROP TABLE IF EXISTS gender");
            statement.executeUpdate("DROP TABLE IF EXISTS city");
        }
        new SchemaDefinition().createSchema(connection);
    }

    public void createMigrationUsersView(Connection connection) throws SQLException {
        new MigrationViews().createMigrationViews(connection);
    }

    private void importData(Connection connection, MigrationData migrationData) throws SQLException {
        DataInserter dataInserter = new DataInserter();
        dataInserter.insertCities(connection, migrationData.cities());
        dataInserter.insertGenders(connection, migrationData.genders());
        dataInserter.insertPeople(connection, migrationData.people());
        dataInserter.insertHobbies(connection, migrationData.hobbies());
        dataInserter.insertPersonInterests(connection, migrationData.personInterests());
        dataInserter.insertRawInterests(connection, migrationData.rawInterests());
        dataInserter.insertPersonLikes(connection, migrationData.personLikes());
        dataInserter.insertPersonMessages(connection, migrationData.personMessages());
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
