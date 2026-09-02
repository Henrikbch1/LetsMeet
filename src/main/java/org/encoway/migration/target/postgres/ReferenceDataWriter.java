package org.encoway.migration.target.postgres;

import org.encoway.migration.model.City;
import org.encoway.migration.model.Gender;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes the small, static reference tables (cities and genders) that other tables refer to.
 */
class ReferenceDataWriter {

    protected void insertCities(Connection connection, List<City> cities) throws SQLException {
        BatchInserter.executeBatch(connection,
                "INSERT INTO %s (city_id, zip_code, city_name) VALUES (?, ?, ?)"
                        .formatted(DatabaseObjectNames.TABLE_CITY),
                cities, (statement, city) -> {
                    statement.setInt(1, city.cityId());
                    statement.setString(2, city.zipCode());
                    statement.setString(3, city.cityName());
                });
    }

    protected void insertGenders(Connection connection, List<Gender> genders) throws SQLException {
        BatchInserter.executeBatch(connection,
                "INSERT INTO %s (gender_id, label) VALUES (?, ?)".formatted(DatabaseObjectNames.TABLE_GENDER),
                genders, (statement, gender) -> {
                    statement.setInt(1, gender.genderId());
                    statement.setString(2, gender.label());
                });
    }
}
