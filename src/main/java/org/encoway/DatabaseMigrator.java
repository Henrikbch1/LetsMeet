package org.encoway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseMigrator {

    String DATABASE_URL = "jdbc:postgresql://localhost:5432/my_database";
    String USER = "user";
    String SECRET = "secret";

    public void init() {

        try (Connection connection = DriverManager.getConnection(DATABASE_URL, USER, SECRET)) {
            connection.createStatement();

            createTables(connection);
            importData(connection);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Drop existing & create new tables */
    public void createTables(Connection connection) {



    }

    public void importData(Connection connection) {

    }

}
