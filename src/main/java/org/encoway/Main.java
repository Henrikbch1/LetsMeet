package org.encoway;

import org.encoway.models.MigrationData;

public class Main {

    public static void main(String[] args) {

        ExcelDataReader excelDataReader = new ExcelDataReader();
        MigrationData migrationData = excelDataReader.readMigrationData();

        String test = "";
    }
}