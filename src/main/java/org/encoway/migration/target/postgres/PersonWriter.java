package org.encoway.migration.target.postgres;

import org.encoway.migration.domain.model.Person;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes {@link Person} rows into the {@code person} table.
 */
class PersonWriter {

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

    protected void insertPeople(Connection connection, List<Person> people) throws SQLException {
        BatchInserter.executeBatch(connection, """
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
}
