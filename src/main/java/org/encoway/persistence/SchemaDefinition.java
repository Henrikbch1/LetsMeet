package org.encoway.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaDefinition {

    public static final int HOBBY_PRIORITY_MIN = -100;
    public static final int HOBBY_PRIORITY_MAX = 100;

    private static final String CREATE_CITY_TABLE = """
            CREATE TABLE %s (
                city_id INT PRIMARY KEY,
                zip_code VARCHAR NOT NULL,
                city_name VARCHAR NOT NULL,
                UNIQUE (zip_code, city_name)
            )
            """.formatted(DatabaseObjectNames.TABLE_CITY);

    private static final String CREATE_GENDER_TABLE = """
            CREATE TABLE %s (
                gender_id INT PRIMARY KEY,
                label VARCHAR NOT NULL
            )
            """.formatted(DatabaseObjectNames.TABLE_GENDER);

    private static final String CREATE_PERSON_TABLE = """
            CREATE TABLE %s (
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
            """.formatted(DatabaseObjectNames.TABLE_PERSON);

    // Email is unique cross-source, case-insensitively (Excel vs. Mongo casing).
    private static final String CREATE_PERSON_EMAIL_LOWER_INDEX =
            "CREATE UNIQUE INDEX person_email_lower_idx ON person (LOWER(email))";

    private static final String CREATE_HOBBY_TABLE = """
            CREATE TABLE %s (
                hobby_id INT PRIMARY KEY,
                user_id INT REFERENCES person(person_id),
                description TEXT,
                priority SMALLINT CHECK (priority BETWEEN %d AND %d)
            )
            """.formatted(DatabaseObjectNames.TABLE_HOBBY, HOBBY_PRIORITY_MIN, HOBBY_PRIORITY_MAX);

    private static final String CREATE_PERSON_INTEREST_TABLE = """
            CREATE TABLE %s (
                person_id INT REFERENCES person(person_id),
                gender_id INT REFERENCES gender(gender_id),
                PRIMARY KEY (person_id, gender_id)
            )
            """.formatted(DatabaseObjectNames.TABLE_PERSON_INTEREST);

    // Raw interest code rows per person.
    private static final String CREATE_PERSON_INTEREST_TEXT_TABLE = """
            CREATE TABLE %s (
                person_id INT REFERENCES person(person_id),
                interest_code TEXT NOT NULL,
                PRIMARY KEY (person_id, interest_code)
            )
            """.formatted(DatabaseObjectNames.TABLE_PERSON_INTEREST_TEXT);

    private static final String CREATE_PERSON_LIKE_TABLE = """
            CREATE TABLE %s (
                like_id INT PRIMARY KEY,
                liker_person_id INT NOT NULL REFERENCES person(person_id),
                liked_person_id INT NOT NULL REFERENCES person(person_id),
                status VARCHAR NOT NULL,
                liked_at TIMESTAMP NOT NULL
            )
            """.formatted(DatabaseObjectNames.TABLE_PERSON_LIKE);

    private static final String CREATE_PERSON_MESSAGE_TABLE = """
            CREATE TABLE %s (
                message_id INT PRIMARY KEY,
                sender_person_id INT NOT NULL REFERENCES person(person_id),
                receiver_person_id INT NOT NULL REFERENCES person(person_id),
                conversation_id INT NOT NULL,
                body TEXT NOT NULL,
                sent_at TIMESTAMP NOT NULL
            )
            """.formatted(DatabaseObjectNames.TABLE_PERSON_MESSAGE);

    public void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_CITY_TABLE);
            statement.executeUpdate(CREATE_GENDER_TABLE);
            statement.executeUpdate(CREATE_PERSON_TABLE);
            statement.executeUpdate(CREATE_PERSON_EMAIL_LOWER_INDEX);
            statement.executeUpdate(CREATE_HOBBY_TABLE);
            statement.executeUpdate(CREATE_PERSON_INTEREST_TABLE);
            statement.executeUpdate(CREATE_PERSON_INTEREST_TEXT_TABLE);
            statement.executeUpdate(CREATE_PERSON_LIKE_TABLE);
            statement.executeUpdate(CREATE_PERSON_MESSAGE_TABLE);
        }
    }
}
