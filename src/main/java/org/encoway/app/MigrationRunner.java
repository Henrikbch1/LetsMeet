package org.encoway.app;

import org.encoway.merge.MigrationDataAssembler;
import org.encoway.model.MigrationData;
import org.encoway.model.MongoData;
import org.encoway.source.excel.ExcelDataReader;
import org.encoway.source.mongo.MongoDataReader;

public class MigrationRunner {

    public void run() {
        MigrationData excelData = new ExcelDataReader().readMigrationData();
        MongoData mongoData = new MongoDataReader().readMongoData();
        MigrationData migrationData = new MigrationDataAssembler().assemble(excelData, mongoData);
        new DatabaseMigrator().migrate(migrationData);
    }
}
