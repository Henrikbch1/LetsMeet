package org.encoway.source.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.encoway.model.MigrationData;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExcelDataReader {

    private static final Path WORKBOOK_PATH = Path.of("Lets Meet DB Dump.xlsx");

    public MigrationData readMigrationData() {
        return readMigrationData(WORKBOOK_PATH);
    }

    public MigrationData readMigrationData(Path workbookPath) {
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            return mapWorkbook(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read workbook: " + workbookPath, exception);
        }
    }

    private MigrationData mapWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        ExcelRowMapper rowMapper = new ExcelRowMapper();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            rowMapper.mapRow(row);
        }
        return rowMapper.toMigrationData();
    }
}
