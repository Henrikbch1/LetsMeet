package org.encoway.migration.application;

import org.encoway.migration.application.assembly.MigrationDataAssembler;
import org.encoway.migration.domain.model.Hobby;
import org.encoway.migration.domain.model.MigrationData;
import org.encoway.migration.source.mongo.model.MongoData;
import org.encoway.migration.source.excel.ExcelDataReader;
import org.encoway.migration.source.mongo.MongoDataReader;
import org.encoway.migration.source.xml.HobbyXmlReader;
import org.encoway.migration.target.postgres.DatabaseMigrator;

import java.util.ArrayList;
import java.util.List;

public class MigrationRunner {

    public void run(String recordsPathPrefix) {
        MigrationData excelData = new ExcelDataReader().readMigrationData();
        MongoData mongoData = new MongoDataReader().readMongoData();
        MigrationData migrationData = new MigrationDataAssembler().assemble(excelData, mongoData);

        List<Hobby> xmlHobbies = new HobbyXmlReader().readHobbies(migrationData.people(), migrationData.hobbies());
        List<Hobby> allHobbies = new ArrayList<>(migrationData.hobbies());
        allHobbies.addAll(xmlHobbies);

        MigrationData enrichedData = new MigrationData(
                migrationData.cities(),
                migrationData.genders(),
                migrationData.people(),
                allHobbies,
                migrationData.personInterests(),
                migrationData.rawInterests(),
                migrationData.personLikes(),
                migrationData.personMessages()
        );

        new DatabaseMigrator(recordsPathPrefix).migrate(enrichedData);
    }
}
