package org.encoway.migration.target.postgres;

import org.encoway.migration.domain.model.PersonInterest;
import org.encoway.migration.domain.model.RawInterest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes {@link PersonInterest} and {@link RawInterest} rows, both describing the interests
 * declared by a person.
 */
class InterestWriter {

    protected void insertPersonInterests(Connection connection, List<PersonInterest> personInterests) throws SQLException {
        BatchInserter.executeBatch(connection,
                "INSERT INTO %s (person_id, gender_id) VALUES (?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_PERSON_INTEREST),
                personInterests, (statement, personInterest) -> {
                    statement.setInt(1, personInterest.personId());
                    statement.setInt(2, personInterest.genderId());
                });
    }

    protected void insertRawInterests(Connection connection, List<RawInterest> rawInterests) throws SQLException {
        BatchInserter.executeBatch(connection,
                "INSERT INTO %s (person_id, interest_code) VALUES (?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_PERSON_INTEREST_TEXT),
                rawInterests, (statement, rawInterest) -> {
                    statement.setInt(1, rawInterest.personId());
                    statement.setString(2, rawInterest.interestCode());
                });
    }
}
