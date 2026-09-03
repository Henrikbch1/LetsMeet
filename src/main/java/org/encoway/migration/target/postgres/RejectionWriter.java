package org.encoway.migration.target.postgres;

import org.encoway.migration.model.MigrationRejection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes {@link MigrationRejection} rows into the {@code migration_rejection} table.
 */
class RejectionWriter {

    protected void insertRejections(Connection connection, List<MigrationRejection> rejections) throws SQLException {
        BatchInserter.executeBatch(connection,
                "INSERT INTO %s (source, source_ref, reason) VALUES (?, ?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_MIGRATION_REJECTION),
                rejections, (statement, rejection) -> {
                    statement.setString(1, rejection.source());
                    statement.setString(2, rejection.sourceRef());
                    statement.setString(3, rejection.reason());
                });
    }
}
