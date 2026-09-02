package org.encoway.migration.target.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Encapsulates the PostgreSQL connection settings so that no other class needs to know the
 * database URL or credentials.
 */
final class DatabaseConfig {

    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/lf8_lets_meet_db";
    private static final String DATABASE_USER = "user";
    private static final String PASSWORD = "secret";

    private DatabaseConfig() {
    }

    protected static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, DATABASE_USER, PASSWORD);
    }
}
