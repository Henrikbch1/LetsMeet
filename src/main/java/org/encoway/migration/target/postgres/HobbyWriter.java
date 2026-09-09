package org.encoway.migration.target.postgres;

import org.encoway.migration.domain.model.Hobby;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Writes {@link Hobby} rows into the {@code hobby} table.
 */
class HobbyWriter {

    private static final String INSERT_HOBBY_SQL =
            "INSERT INTO %s (hobby_id, user_id, description, priority, source) VALUES (?, ?, ?, ?, ?)"
                    .formatted(DatabaseObjectNames.TABLE_HOBBY);

    protected void insertHobbies(Connection connection, List<Hobby> hobbies) throws SQLException {
        BatchInserter.executeBatch(connection, INSERT_HOBBY_SQL, hobbies, this::bind);
    }

    /**
     * Inserts a single hobby outside of the batch, so the caller can isolate a per-record
     * database fault (e.g. a duplicate person/hobby fact) with a savepoint instead of aborting
     * the whole import.
     */
    protected void insertHobby(Connection connection, Hobby hobby) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_HOBBY_SQL)) {
            bind(statement, hobby);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Hobby hobby) throws SQLException {
        statement.setInt(1, hobby.hobbyId());
        statement.setInt(2, hobby.userId());
        statement.setString(3, hobby.description());
        if (hobby.priority() == null) {
            statement.setNull(4, Types.INTEGER);
        } else {
            statement.setInt(4, hobby.priority());
        }
        statement.setString(5, hobby.source());
    }
}
