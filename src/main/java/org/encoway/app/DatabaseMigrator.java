package org.encoway.app;

import org.encoway.merge.MigrationDataAssembler;
import org.encoway.model.MigrationData;
import org.encoway.model.MongoData;
import org.encoway.persistence.DataInserter;
import org.encoway.persistence.MigrationViews;
import org.encoway.persistence.SchemaDefinition;
import org.encoway.source.excel.ExcelDataReader;
import org.encoway.source.mongo.MongoDataReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseMigrator {

    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/lf8_lets_meet_db";
    private static final String USER = "user";
    private static final String SECRET = "secret";

    public void migrate() {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, USER, SECRET)) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                createTables(connection);
                importData(connection);
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

    public void importData(Connection connection) throws SQLException {
        MigrationData excelData = new ExcelDataReader().readMigrationData();
        MongoData mongoData = new MongoDataReader().readMongoData();
        MigrationData migrationData = new MigrationDataAssembler().assemble(excelData, mongoData);
        importData(connection, migrationData);
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
