package org.encoway.migration.target.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Shared batch-insert execution used by the table-specific writers in this package.
 */
final class BatchInserter {

    private BatchInserter() {
    }

    protected static <T> void executeBatch(Connection connection, String sql, List<T> items, RowBinder<T> binder)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (T item : items) {
                binder.bind(statement, item);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @FunctionalInterface
    interface RowBinder<T> {
        public void bind(PreparedStatement statement, T item) throws SQLException;
    }
}
