package org.encoway.migration.target.postgres;

import org.encoway.migration.model.Hobby;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes {@link Hobby} rows into the {@code hobby} table.
 */
class HobbyWriter {

    protected void insertHobbies(Connection connection, List<Hobby> hobbies) throws SQLException {
        BatchInserter.executeBatch(connection,
                "INSERT INTO %s (hobby_id, user_id, description, priority) VALUES (?, ?, ?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_HOBBY),
                hobbies, (statement, hobby) -> {
                    statement.setInt(1, hobby.hobbyId());
                    statement.setInt(2, hobby.userId());
                    statement.setString(3, hobby.description());
                    statement.setInt(4, hobby.priority());
                });
    }
}
