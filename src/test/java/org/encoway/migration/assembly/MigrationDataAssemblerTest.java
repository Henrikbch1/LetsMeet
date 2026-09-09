package org.encoway.migration.assembly;

import org.encoway.migration.model.City;
import org.encoway.migration.model.Gender;
import org.encoway.migration.model.Hobby;
import org.encoway.migration.model.MigrationData;
import org.encoway.migration.model.MongoData;
import org.encoway.migration.model.MongoLike;
import org.encoway.migration.model.MongoMessage;
import org.encoway.migration.model.MongoProfile;
import org.encoway.migration.model.Person;
import org.encoway.migration.model.PersonLike;
import org.encoway.migration.model.PersonMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class MigrationDataAssemblerTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 2);
    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2024, 5, 6, 7, 8);

    @Test
    void assemble_mongoWinsForProfileFields_andResolvesRelations() {
        // Arrange
        final var excelPerson = person(1, "USER@example.com", "Excel Last", "Excel First", "111");
        final var otherPerson = person(2, "other@example.com", "Other Last", "Other First", "333");
        final var excelData = migrationData(List.of(excelPerson, otherPerson));
        final var mongoData = new MongoData(
                List.of(new MongoProfile("user@example.com", "Mongo First", "Mongo Last", "222")),
                List.of(new MongoLike("user@example.com", "OTHER@example.com", "accepted", EVENT_TIME)),
                List.of(new MongoMessage("OTHER@example.com", "user@example.com", 4, "Hello", EVENT_TIME)));

        // Act
        final var result = new MigrationDataAssembler().assemble(excelData, mongoData);

        // Assert
        assertThat(result.people()).extracting(Person::lastName, Person::firstName, Person::phoneNumber)
                .containsExactly(
                        tuple("Mongo Last", "Mongo First", "222"),
                        tuple("Other Last", "Other First", "333"));
        assertThat(result.personLikes()).containsExactly(
                new PersonLike(1, 1, 2, "accepted", EVENT_TIME));
        assertThat(result.personMessages()).containsExactly(
                new PersonMessage(1, 2, 1, 4, "Hello", EVENT_TIME));
    }

    @Test
    void assemble_excelWinsForProfileFields() {
        // Arrange
        final var excelPerson = person(1, "user@example.com", "Excel Last", "", "");
        final var excelData = migrationData(List.of(excelPerson));
        final var mongoData = new MongoData(
                List.of(new MongoProfile("USER@example.com", "Mongo First", "Mongo Last", "222")),
                List.of(),
                List.of());

        // Act
        final var result = new MigrationDataAssembler(ProfileConflictPolicy.EXCEL_WINS)
                .assemble(excelData, mongoData);

        // Assert
        assertThat(result.people()).extracting(Person::lastName, Person::firstName, Person::phoneNumber)
                .containsExactly(tuple("Excel Last", "Mongo First", "222"));
    }

    @Test
    void assemble_whenRelationReferencesUnknownPerson_rejectsTheAssembly() {
        // Arrange
        final var excelData = migrationData(List.of(person(1, "known@example.com",
                "Last", "First", "111")));
        final var mongoData = new MongoData(
                List.of(),
                List.of(new MongoLike("known@example.com", "missing@example.com", "pending", EVENT_TIME)),
                List.of());

        // Act & Assert
        assertThatThrownBy(() -> new MigrationDataAssembler().assemble(excelData, mongoData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unresolved relation reference");
    }

    private MigrationData migrationData(List<Person> people) {
        return new MigrationData(
                List.of(new City(1, "12345", "City")),
                List.of(new Gender(1, "m")),
                people,
                List.of(new Hobby(1, 1, "Hobby", 1, "excel")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private Person person(int id, String email, String lastName, String firstName, String phone) {
        return new Person(id, lastName, firstName, "Street", "1", 1, phone, email, 1, BIRTH_DATE);
    }
}
