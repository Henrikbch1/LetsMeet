package org.encoway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseMigrator {

    String DATABASE_URL = "jdbc:postgresql://localhost:5432/lf8_lets_meet_db";
    String USER = "user";
    String SECRET = "secret";

    /*public static void main(String[] args) {
        DatabaseMigrator migrator = new DatabaseMigrator();
        migrator.init();
    }*/

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
    public void createTables(Connection connection) throws SQLException {

        Statement statement = connection.createStatement();
        statement.execute("""
                    DROP TABLE IF EXISTS CITY CASCADE;
                    CREATE TABLE CITY(
                        zip_code VARCHAR PRIMARY KEY,
                        city_name VARCHAR NOT NULL
                    );
                    
                    DROP TABLE IF EXISTS GENDER CASCADE;
                    CREATE TABLE GENDER(
                        gender_id SERIAL PRIMARY KEY,
                        label VARCHAR NOT NULL -- 'm' | 'w' | 'nb'
                    );
                    
                    DROP TABLE IF EXISTS PERSON CASCADE ;
                    CREATE TABLE PERSON(
                        person_id SERIAL PRIMARY KEY,
                        last_name VARCHAR NOT NULL,
                        first_name VARCHAR NOT NULL,
                        street VARCHAR,
                        zip_code VARCHAR REFERENCES CITY(zip_code),
                        phone_number VARCHAR,
                        email VARCHAR UNIQUE NOT NULL,
                        gender_id INT REFERENCES GENDER(gender_id),
                        birth_date DATE
                    );
                    
                    DROP TABLE IF EXISTS HOBBY CASCADE;
                    CREATE TABLE HOBBY(
                        hobby_id SERIAL PRIMARY KEY,
                        user_id INT REFERENCES PERSON("person_id"),
                        description TEXT,
                        priority SMALLINT CHECK (priority BETWEEN 0 AND 100)
                    );
                    
                    DROP TABLE IF EXISTS PERSON_INTEREST CASCADE ;
                    CREATE TABLE PERSON_INTEREST(
                        person_id INT REFERENCES PERSON(person_id),
                        gender_id INT REFERENCES GENDER(gender_id),
                        PRIMARY KEY (person_id, gender_id)
                    );
                    """);
    }

    public void importData(Connection connection) {

    }

}
