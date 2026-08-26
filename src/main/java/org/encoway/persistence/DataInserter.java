package org.encoway.persistence;

import org.encoway.model.City;
import org.encoway.model.Gender;
import org.encoway.model.Hobby;
import org.encoway.model.Person;
import org.encoway.model.PersonInterest;
import org.encoway.model.PersonLike;
import org.encoway.model.PersonMessage;
import org.encoway.model.RawInterest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class DataInserter {

    private static final int PERSON_ID = 1;
    private static final int LAST_NAME = 2;
    private static final int FIRST_NAME = 3;
    private static final int STREET = 4;
    private static final int STREET_NUMBER = 5;
    private static final int CITY_ID = 6;
    private static final int PHONE_NUMBER = 7;
    private static final int EMAIL = 8;
    private static final int GENDER_ID = 9;
    private static final int BIRTH_DATE = 10;

    public void insertCities(Connection connection, List<City> cities) throws SQLException {
        executeBatch(connection,
                "INSERT INTO %s (city_id, zip_code, city_name) VALUES (?, ?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_CITY),
                cities, (statement, city) -> {
                    statement.setInt(1, city.cityId());
                    statement.setString(2, city.zipCode());
                    statement.setString(3, city.cityName());
                });
    }

    public void insertGenders(Connection connection, List<Gender> genders) throws SQLException {
        executeBatch(connection,
                "INSERT INTO %s (gender_id, label) VALUES (?, ?)".formatted(DatabaseObjectNames.TABLE_GENDER),
                genders, (statement, gender) -> {
                    statement.setInt(1, gender.genderId());
                    statement.setString(2, gender.label());
                });
    }

    public void insertPeople(Connection connection, List<Person> people) throws SQLException {
        executeBatch(connection, """
                INSERT INTO %s (
                    person_id, last_name, first_name, street, street_number, city_id, phone_number,
                    email, gender_id, birth_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(DatabaseObjectNames.TABLE_PERSON), people, (statement, person) -> {
                    statement.setInt(PERSON_ID, person.personId());
                    statement.setString(LAST_NAME, person.lastName());
                    statement.setString(FIRST_NAME, person.firstName());
                    statement.setString(STREET, person.street());
                    statement.setString(STREET_NUMBER, person.streetNumber());
                    statement.setInt(CITY_ID, person.cityId());
                    statement.setString(PHONE_NUMBER, person.phoneNumber());
                    statement.setString(EMAIL, person.email());
                    statement.setInt(GENDER_ID, person.genderId());
                    statement.setObject(BIRTH_DATE, person.birthDate());
                });
    }

    public void insertHobbies(Connection connection, List<Hobby> hobbies) throws SQLException {
        executeBatch(connection,
                "INSERT INTO %s (hobby_id, user_id, description, priority) VALUES (?, ?, ?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_HOBBY),
                hobbies, (statement, hobby) -> {
                    statement.setInt(1, hobby.hobbyId());
                    statement.setInt(2, hobby.userId());
                    statement.setString(3, hobby.description());
                    statement.setInt(4, hobby.priority());
                });
    }

    public void insertPersonInterests(Connection connection, List<PersonInterest> personInterests)
            throws SQLException {
        executeBatch(connection,
                "INSERT INTO %s (person_id, gender_id) VALUES (?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_PERSON_INTEREST),
                personInterests, (statement, personInterest) -> {
                    statement.setInt(1, personInterest.personId());
                    statement.setInt(2, personInterest.genderId());
                });
    }

    public void insertRawInterests(Connection connection, List<RawInterest> rawInterests) throws SQLException {
        executeBatch(connection,
                "INSERT INTO %s (person_id, interest_code) VALUES (?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_PERSON_INTEREST_TEXT),
                rawInterests, (statement, rawInterest) -> {
                    statement.setInt(1, rawInterest.personId());
                    statement.setString(2, rawInterest.interestCode());
                });
    }

    public void insertPersonLikes(Connection connection, List<PersonLike> personLikes) throws SQLException {
        executeBatch(connection, """
                INSERT INTO %s (like_id, liker_person_id, liked_person_id, status, liked_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(DatabaseObjectNames.TABLE_PERSON_LIKE), personLikes, (statement, personLike) -> {
                    statement.setInt(1, personLike.likeId());
                    statement.setInt(2, personLike.likerPersonId());
                    statement.setInt(3, personLike.likedPersonId());
                    statement.setString(4, personLike.status());
                    statement.setObject(5, personLike.likedAt());
                });
    }

    public void insertPersonMessages(Connection connection, List<PersonMessage> personMessages)
            throws SQLException {
        executeBatch(connection, """
                INSERT INTO %s (
                    message_id, sender_person_id, receiver_person_id, conversation_id, body, sent_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(DatabaseObjectNames.TABLE_PERSON_MESSAGE), personMessages, (statement, personMessage) -> {
                    statement.setInt(1, personMessage.messageId());
                    statement.setInt(2, personMessage.senderPersonId());
                    statement.setInt(3, personMessage.receiverPersonId());
                    statement.setInt(4, personMessage.conversationId());
                    statement.setString(5, personMessage.body());
                    statement.setObject(6, personMessage.sentAt());
                });
    }

    private <T> void executeBatch(Connection connection, String sql, List<T> items, BatchParameterMapper<T> mapper)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (T item : items) {
                mapper.mapParameters(statement, item);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @FunctionalInterface
    private interface BatchParameterMapper<T> {
        void mapParameters(PreparedStatement statement, T item) throws SQLException;
    }
}
