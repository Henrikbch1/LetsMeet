package org.encoway.migration.application;

import org.encoway.migration.assembly.MigrationDataAssembler;
import org.encoway.migration.model.MigrationData;
import org.encoway.migration.model.MongoData;
import org.encoway.migration.source.excel.ExcelDataReader;
import org.encoway.migration.source.mongo.MongoDataReader;
import org.encoway.migration.target.postgres.DatabaseMigrator;

public class MigrationRunner {

    public void run() {
        MigrationData excelData = new ExcelDataReader().readMigrationData();
        MongoData mongoData = new MongoDataReader().readMongoData();
        MigrationData migrationData = new MigrationDataAssembler().assemble(excelData, mongoData);
        new DatabaseMigrator().migrate(migrationData);
    }
}
