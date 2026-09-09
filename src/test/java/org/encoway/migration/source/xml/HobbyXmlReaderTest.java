package org.encoway.migration.source.xml;

import org.encoway.migration.domain.model.Hobby;
import org.encoway.migration.domain.model.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HobbyXmlReaderTest {

    private final HobbyXmlReader underTest = new HobbyXmlReader();

    @Test
    void readHobbies_resolvesPersonByCaseInsensitiveEmail_andSkipsUnresolvedUsers(@TempDir Path tempDir)
            throws IOException {
        // Arrange
        Path xmlFile = writeXml(tempDir, """
                <?xml version="1.0"?>
                <users>
                    <user>
                        <email>Known@Example.COM</email>
                        <name>Doe, Jane</name>
                        <hobbies>
                            <hobby>Yoga</hobby>
                        </hobbies>
                    </user>
                    <user>
                        <email>unknown@example.com</email>
                        <name>Nobody, Person</name>
                        <hobbies>
                            <hobby>Skydiving</hobby>
                        </hobbies>
                    </user>
                </users>
                """);
        List<Person> people = List.of(person(1, "known@example.com"));

        // Act
        List<Hobby> result = underTest.readHobbies(xmlFile, people, List.of());

        // Assert
        assertThat(result).containsExactly(new Hobby(1, 1, "Yoga", null, "xml"));
    }

    @Test
    void readHobbies_existingExcelFactWins_soMatchingXmlFactIsNotDuplicated(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path xmlFile = writeXml(tempDir, """
                <users>
                    <user>
                        <email>known@example.com</email>
                        <name>Doe, Jane</name>
                        <hobbies>
                            <hobby>Yoga</hobby>
                            <hobby>Cooking</hobby>
                        </hobbies>
                    </user>
                </users>
                """);
        List<Person> people = List.of(person(1, "known@example.com"));
        List<Hobby> existingHobbies = List.of(new Hobby(5, 1, "Yoga", 42, "excel"));

        // Act
        List<Hobby> result = underTest.readHobbies(xmlFile, people, existingHobbies);

        // Assert
        assertThat(result).containsExactly(new Hobby(6, 1, "Cooking", null, "xml"));
    }

    @Test
    void readHobbies_firstXmlOccurrenceWins_soARepeatedFactWithinXmlIsNotDuplicated(@TempDir Path tempDir)
            throws IOException {
        // Arrange
        Path xmlFile = writeXml(tempDir, """
                <users>
                    <user>
                        <email>known@example.com</email>
                        <name>Doe, Jane</name>
                        <hobbies>
                            <hobby>Yoga</hobby>
                            <hobby>Yoga</hobby>
                        </hobbies>
                    </user>
                </users>
                """);
        List<Person> people = List.of(person(1, "known@example.com"));

        // Act
        List<Hobby> result = underTest.readHobbies(xmlFile, people, List.of());

        // Assert
        assertThat(result).containsExactly(new Hobby(1, 1, "Yoga", null, "xml"));
    }

    private Path writeXml(Path dir, String content) throws IOException {
        Path file = dir.resolve("hobbies.xml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private Person person(int id, String email) {
        return new Person(id, "Last", "First", "Street", "1", 1, "000", email, 1, LocalDate.of(2000, 1, 1));
    }
}
