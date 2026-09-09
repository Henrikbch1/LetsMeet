package org.encoway.migration.source.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.encoway.migration.domain.model.City;
import org.encoway.migration.domain.model.Hobby;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ExcelDataReaderTest {

    private final ExcelDataReader underTest = new ExcelDataReader();

    @Test
    void readMigrationData_mapsPeopleHobbiesInterestsAndSharedCities(@TempDir Path tempDir) throws IOException {
        // Arrange
        final var workbookPath = tempDir.resolve("migration.xlsx");
        writeWorkbook(workbookPath);

        // Act
        final var result = underTest.readMigrationData(workbookPath);

        // Assert
        assertThat(result.people()).extracting(
                        org.encoway.migration.domain.model.Person::personId,
                        org.encoway.migration.domain.model.Person::lastName,
                        org.encoway.migration.domain.model.Person::firstName,
                        org.encoway.migration.domain.model.Person::streetNumber,
                        org.encoway.migration.domain.model.Person::cityId,
                        org.encoway.migration.domain.model.Person::genderId)
                .containsExactly(
                        tuple(1, "Doe", "Jane", "12", 1, 2),
                        tuple(2, "Roe", "John", "12", 1, 1));
        assertThat(result.cities()).containsExactly(
                new City(1, "69115", "Heidelberg"));
        assertThat(result.hobbies()).containsExactly(
                new Hobby(1, 1, "Reading", 3, "excel"));
        assertThat(result.rawInterests()).extracting(
                        org.encoway.migration.domain.model.RawInterest::personId,
                        org.encoway.migration.domain.model.RawInterest::interestCode)
                .containsExactly(
                        tuple(1, "m"),
                        tuple(1, "w"));
    }

    @Test
    void readMigrationData_whenWorkbookDoesNotExist_wrapsTheIoFailure(@TempDir Path tempDir) {
        // Arrange
        final var missingWorkbook = tempDir.resolve("missing.xlsx");

        // Act & Assert
        assertThatThrownBy(() -> underTest.readMigrationData(missingWorkbook))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Could not read workbook");
    }

    private void writeWorkbook(Path workbookPath) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            final var sheet = workbook.createSheet("Users");
            final var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            final var firstRow = sheet.createRow(1);
            firstRow.createCell(0).setCellValue("Doe, Jane");
            firstRow.createCell(1).setCellValue("Main Street 12, 69115, Heidelberg");
            firstRow.createCell(2).setCellValue("123");
            firstRow.createCell(3).setCellValue("Reading % 3%");
            firstRow.createCell(4).setCellValue("jane@example.com");
            firstRow.createCell(5).setCellValue("w");
            firstRow.createCell(6).setCellValue("mw");
            firstRow.createCell(7).setCellValue("02.01.1990");

            final var secondRow = sheet.createRow(2);
            secondRow.createCell(0).setCellValue("Roe, John");
            secondRow.createCell(1).setCellValue("Main Street 12, 69115, Heidelberg");
            secondRow.createCell(2).setCellValue("456");
            secondRow.createCell(3).setCellValue("");
            secondRow.createCell(4).setCellValue("john@example.com");
            secondRow.createCell(5).setCellValue("m");
            secondRow.createCell(6).setCellValue("");
            secondRow.createCell(7).setCellValue("03.01.1990");

            try (var outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }
    }
}
