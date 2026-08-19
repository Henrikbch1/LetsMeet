package org.encoway;

import org.encoway.models.City;
import org.encoway.models.Gender;
import org.encoway.models.Hobby;
import org.encoway.models.MigrationData;
import org.encoway.models.Person;
import org.encoway.models.PersonInterest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DatabaseMigrator {

    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/lf8_lets_meet_db";
    private static final String USER = "user";
    private static final String SECRET = "secret";

    public void init() {
        migrate();
    }

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
            statement.executeUpdate("DROP VIEW IF EXISTS migration_users");
            statement.executeUpdate("DROP TABLE IF EXISTS person_interest");
            statement.executeUpdate("DROP TABLE IF EXISTS hobby");
            statement.executeUpdate("DROP TABLE IF EXISTS person");
            statement.executeUpdate("DROP TABLE IF EXISTS gender");
            statement.executeUpdate("DROP TABLE IF EXISTS city");

            statement.executeUpdate("""
                    CREATE TABLE city (
                        city_id INT PRIMARY KEY,
                        zip_code VARCHAR NOT NULL,
                        city_name VARCHAR NOT NULL,
                        UNIQUE (zip_code, city_name)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE gender (
                        gender_id INT PRIMARY KEY,
                        label VARCHAR NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE person (
                        person_id INT PRIMARY KEY,
                        last_name VARCHAR NOT NULL,
                        first_name VARCHAR NOT NULL,
                        street VARCHAR,
                        street_number VARCHAR,
                        city_id INT REFERENCES city(city_id),
                        phone_number VARCHAR,
                        email VARCHAR UNIQUE NOT NULL,
                        gender_id INT REFERENCES gender(gender_id),
                        birth_date DATE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE hobby (
                        hobby_id INT PRIMARY KEY,
                        user_id INT REFERENCES person(person_id),
                        description TEXT,
                        priority SMALLINT CHECK (priority BETWEEN 0 AND 100)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE person_interest (
                        person_id INT REFERENCES person(person_id),
                        gender_id INT REFERENCES gender(gender_id),
                        PRIMARY KEY (person_id, gender_id)
                    )
                    """);
        }
    }

    public void importData(Connection connection) throws SQLException {
        importData(connection, new ExcelDataReader().readMigrationData());
    }

    public void createMigrationUsersView(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP VIEW IF EXISTS migration_users");
            statement.executeUpdate("""
                    CREATE VIEW migration_users AS
                    SELECT
                        person.email::text AS email,
                        person.first_name::text AS first_name,
                        person.last_name::text AS last_name,
                        person.birth_date,
                        city.zip_code::text AS postal_code,
                        city.city_name::text AS city
                    FROM person
                    JOIN city ON city.city_id = person.city_id
                    """);
        }
    }

    private void importData(Connection connection, MigrationData migrationData) throws SQLException {
        insertCities(connection, migrationData.cities());
        insertGenders(connection, migrationData.genders());
        insertPeople(connection, migrationData.people());
        insertHobbies(connection, migrationData.hobbies());
        insertPersonInterests(connection, migrationData.personInterests());
    }

    private void insertCities(Connection connection, List<City> cities) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO city (city_id, zip_code, city_name) VALUES (?, ?, ?)")) {
            for (City city : cities) {
                statement.setInt(1, city.cityId());
                statement.setString(2, city.zipCode());
                statement.setString(3, city.cityName());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertGenders(Connection connection, List<Gender> genders) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gender (gender_id, label) VALUES (?, ?)")) {
            for (Gender gender : genders) {
                statement.setInt(1, gender.genderId());
                statement.setString(2, gender.label());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertPeople(Connection connection, List<Person> people) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO person (
                    person_id, last_name, first_name, street, street_number, city_id, phone_number,
                    email, gender_id, birth_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Person person : people) {
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
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertHobbies(Connection connection, List<Hobby> hobbies) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hobby (hobby_id, user_id, description, priority) VALUES (?, ?, ?, ?)")) {
            for (Hobby hobby : hobbies) {
                statement.setInt(1, hobby.hobbyId());
                statement.setInt(2, hobby.userId());
                statement.setString(3, hobby.description());
                statement.setInt(4, hobby.priority());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertPersonInterests(Connection connection, List<PersonInterest> personInterests)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO person_interest (person_id, gender_id) VALUES (?, ?)")) {
            for (PersonInterest personInterest : personInterests) {
                statement.setInt(1, personInterest.personId());
                statement.setInt(2, personInterest.genderId());
                statement.addBatch();
            }
            statement.executeBatch();
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
