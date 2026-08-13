package org.encoway;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelDataImporter {

    public void init() {
        String workingDirectory = System.getProperty("user.dir");
        String fileName = "Lets Meet DB Dump.xlsx";
        File file = new File(workingDirectory, fileName);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            try(Workbook workbook = new XSSFWorkbook(fileInputStream)) {
                importData(workbook);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void importData(Workbook workbook) {

        Sheet sheet = workbook.getSheet("Tabelle1");
        List<List<String>> rows = new ArrayList<>();

        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            for (Cell cell : row) {
                cells.add(cell.getStringCellValue());
            }
            rows.add(cells);
        }

        String test = "";
    }



}
