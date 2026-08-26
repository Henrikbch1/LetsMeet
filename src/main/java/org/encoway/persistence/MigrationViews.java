package org.encoway.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationViews {

    private static final String VIEW_MIGRATION_USERS = "migration_users";
    private static final String VIEW_MIGRATION_USER_INTERESTS = "migration_user_interests";
    private static final String VIEW_MIGRATION_USER_HOBBIES = "migration_user_hobbies";
    private static final String VIEW_MIGRATION_LIKES = "migration_likes";
    private static final String VIEW_MIGRATION_MESSAGES = "migration_messages";

    private static final String SOURCE_EXCEL = "excel";

    private static final String CREATE_MIGRATION_USERS_VIEW = """
            CREATE VIEW %s AS
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
            """.formatted(VIEW_MIGRATION_USERS);

    private static final String CREATE_MIGRATION_USER_INTERESTS_VIEW = """
            CREATE VIEW %s AS
            SELECT
                person.email::text AS email,
                person_interest_text.interest_code::text AS interest_code
            FROM person_interest_text
            JOIN person ON person.person_id = person_interest_text.person_id
            """.formatted(VIEW_MIGRATION_USER_INTERESTS);

    private static final String CREATE_MIGRATION_USER_HOBBIES_VIEW = """
            CREATE VIEW %s AS
            SELECT
                person.email::text AS email,
                hobby.description::text AS hobby_name,
                hobby.priority::integer AS priority,
                '%s'::text AS source
            FROM hobby
            JOIN person ON person.person_id = hobby.user_id
            """.formatted(VIEW_MIGRATION_USER_HOBBIES, SOURCE_EXCEL);

    private static final String CREATE_MIGRATION_LIKES_VIEW = """
            CREATE VIEW %s AS
            SELECT
                liker.email::text AS liker_email,
                liked.email::text AS liked_email,
                person_like.status::text AS status,
                person_like.liked_at::timestamp AS liked_at
            FROM person_like
            JOIN person liker ON liker.person_id = person_like.liker_person_id
            JOIN person liked ON liked.person_id = person_like.liked_person_id
            """.formatted(VIEW_MIGRATION_LIKES);

    private static final String CREATE_MIGRATION_MESSAGES_VIEW = """
            CREATE VIEW %s AS
            SELECT
                sender.email::text AS sender_email,
                receiver.email::text AS receiver_email,
                person_message.body::text AS body,
                person_message.sent_at::timestamp AS sent_at,
                person_message.conversation_id::integer AS conversation_id
            FROM person_message
            JOIN person sender ON sender.person_id = person_message.sender_person_id
            JOIN person receiver ON receiver.person_id = person_message.receiver_person_id
            """.formatted(VIEW_MIGRATION_MESSAGES);

    public void dropMigrationViews(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            dropMigrationViews(statement);
        }
    }

    public void createMigrationViews(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            dropMigrationViews(statement);
            statement.executeUpdate(CREATE_MIGRATION_USERS_VIEW);
            statement.executeUpdate(CREATE_MIGRATION_USER_INTERESTS_VIEW);
            statement.executeUpdate(CREATE_MIGRATION_USER_HOBBIES_VIEW);
            statement.executeUpdate(CREATE_MIGRATION_LIKES_VIEW);
            statement.executeUpdate(CREATE_MIGRATION_MESSAGES_VIEW);
        }
    }

    private void dropMigrationViews(Statement statement) throws SQLException {
        statement.executeUpdate("DROP VIEW IF EXISTS %s".formatted(VIEW_MIGRATION_MESSAGES));
        statement.executeUpdate("DROP VIEW IF EXISTS %s".formatted(VIEW_MIGRATION_LIKES));
        statement.executeUpdate("DROP VIEW IF EXISTS %s".formatted(VIEW_MIGRATION_USER_HOBBIES));
        statement.executeUpdate("DROP VIEW IF EXISTS %s".formatted(VIEW_MIGRATION_USER_INTERESTS));
        statement.executeUpdate("DROP VIEW IF EXISTS %s".formatted(VIEW_MIGRATION_USERS));
    }
}
