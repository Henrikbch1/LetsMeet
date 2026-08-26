package org.encoway.app;

import org.encoway.merge.MigrationDataAssembler;
import org.encoway.model.City;
import org.encoway.model.Gender;
import org.encoway.model.Hobby;
import org.encoway.model.MigrationData;
import org.encoway.model.MongoData;
import org.encoway.model.Person;
import org.encoway.model.PersonInterest;
import org.encoway.model.PersonLike;
import org.encoway.model.PersonMessage;
import org.encoway.model.RawInterest;
import org.encoway.source.excel.ExcelDataReader;
import org.encoway.source.mongo.MongoDataReader;

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
            statement.executeUpdate("DROP VIEW IF EXISTS migration_messages");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_likes");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_user_hobbies");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_user_interests");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_users");
            statement.executeUpdate("DROP TABLE IF EXISTS person_message");
            statement.executeUpdate("DROP TABLE IF EXISTS person_like");
            statement.executeUpdate("DROP TABLE IF EXISTS person_interest_text");
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
            // Email is unique cross-source, case-insensitively (Excel vs. Mongo casing).
            statement.executeUpdate(
                    "CREATE UNIQUE INDEX person_email_lower_idx ON person (LOWER(email))");
            statement.executeUpdate("""
                    CREATE TABLE hobby (
                        hobby_id INT PRIMARY KEY,
                        user_id INT REFERENCES person(person_id),
                        description TEXT,
                        priority SMALLINT CHECK (priority BETWEEN -100 AND 100)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE person_interest (
                        person_id INT REFERENCES person(person_id),
                        gender_id INT REFERENCES gender(gender_id),
                        PRIMARY KEY (person_id, gender_id)
                    )
                    """);
            // Raw interest code rows per person.
            statement.executeUpdate("""
                    CREATE TABLE person_interest_text (
                        person_id INT REFERENCES person(person_id),
                        interest_code TEXT NOT NULL,
                        PRIMARY KEY (person_id, interest_code)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE person_like (
                        like_id INT PRIMARY KEY,
                        liker_person_id INT NOT NULL REFERENCES person(person_id),
                        liked_person_id INT NOT NULL REFERENCES person(person_id),
                        status VARCHAR NOT NULL,
                        liked_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE person_message (
                        message_id INT PRIMARY KEY,
                        sender_person_id INT NOT NULL REFERENCES person(person_id),
                        receiver_person_id INT NOT NULL REFERENCES person(person_id),
                        conversation_id INT NOT NULL,
                        body TEXT NOT NULL,
                        sent_at TIMESTAMP NOT NULL
                    )
                    """);
        }
    }

    public void importData(Connection connection) throws SQLException {
        MigrationData excelData = new ExcelDataReader().readMigrationData();
        MongoData mongoData = new MongoDataReader().readMongoData();
        MigrationData migrationData = new MigrationDataAssembler().assemble(excelData, mongoData);
        importData(connection, migrationData);
    }

    public void createMigrationUsersView(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP VIEW IF EXISTS migration_messages");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_likes");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_user_hobbies");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_user_interests");
            statement.executeUpdate("DROP VIEW IF EXISTS migration_users");

            statement.executeUpdate("""
                    CREATE VIEW migration_users AS
                    SELECT
                        person.email::text AS email,
                        person.first_name::text AS first_name,
                        person.last_name::text AS last_name,
                        person.birth_date::date AS birth_date,
                        city.zip_code::text AS postal_code,
                        city.city_name::text AS city,
                        person.phone_number::text AS phone,
                        gender.label::text AS gender
                    FROM person
                    JOIN city ON city.city_id = person.city_id
                    JOIN gender ON gender.gender_id = person.gender_id
                    """);
            statement.executeUpdate("""
                    CREATE VIEW migration_user_interests AS
                    SELECT
                        person.email::text AS email,
                        person_interest_text.interest_code::text AS interest_code
                    FROM person_interest_text
                    JOIN person ON person.person_id = person_interest_text.person_id
                    """);
            statement.executeUpdate("""
                    CREATE VIEW migration_user_hobbies AS
                    SELECT
                        person.email::text AS email,
                        hobby.description::text AS hobby_name,
                        hobby.priority::integer AS priority,
                        'excel'::text AS source
                    FROM hobby
                    JOIN person ON person.person_id = hobby.user_id
                    """);
            statement.executeUpdate("""
                    CREATE VIEW migration_likes AS
                    SELECT
                        liker.email::text AS liker_email,
                        liked.email::text AS liked_email,
                        person_like.status::text AS status,
                        person_like.liked_at::timestamp AS liked_at
                    FROM person_like
                    JOIN person liker ON liker.person_id = person_like.liker_person_id
                    JOIN person liked ON liked.person_id = person_like.liked_person_id
                    """);
            statement.executeUpdate("""
                    CREATE VIEW migration_messages AS
                    SELECT
                        sender.email::text AS sender_email,
                        receiver.email::text AS receiver_email,
                        person_message.body::text AS body,
                        person_message.sent_at::timestamp AS sent_at,
                        person_message.conversation_id::integer AS conversation_id
                    FROM person_message
                    JOIN person sender ON sender.person_id = person_message.sender_person_id
                    JOIN person receiver ON receiver.person_id = person_message.receiver_person_id
                    """);
        }
    }

    private void importData(Connection connection, MigrationData migrationData) throws SQLException {
        insertCities(connection, migrationData.cities());
        insertGenders(connection, migrationData.genders());
        insertPeople(connection, migrationData.people());
        insertHobbies(connection, migrationData.hobbies());
        insertPersonInterests(connection, migrationData.personInterests());
        insertRawInterests(connection, migrationData.rawInterests());
        insertPersonLikes(connection, migrationData.personLikes());
        insertPersonMessages(connection, migrationData.personMessages());
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

    private void insertRawInterests(Connection connection, List<RawInterest> rawInterests) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO person_interest_text (person_id, interest_code) VALUES (?, ?)")) {
            for (RawInterest rawInterest : rawInterests) {
                statement.setInt(1, rawInterest.personId());
                statement.setString(2, rawInterest.interestCode());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertPersonLikes(Connection connection, List<PersonLike> personLikes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO person_like (like_id, liker_person_id, liked_person_id, status, liked_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (PersonLike personLike : personLikes) {
                statement.setInt(1, personLike.likeId());
                statement.setInt(2, personLike.likerPersonId());
                statement.setInt(3, personLike.likedPersonId());
                statement.setString(4, personLike.status());
                statement.setObject(5, personLike.likedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertPersonMessages(Connection connection, List<PersonMessage> personMessages)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO person_message (
                    message_id, sender_person_id, receiver_person_id, conversation_id, body, sent_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (PersonMessage personMessage : personMessages) {
                statement.setInt(1, personMessage.messageId());
                statement.setInt(2, personMessage.senderPersonId());
                statement.setInt(3, personMessage.receiverPersonId());
                statement.setInt(4, personMessage.conversationId());
                statement.setString(5, personMessage.body());
                statement.setObject(6, personMessage.sentAt());
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
