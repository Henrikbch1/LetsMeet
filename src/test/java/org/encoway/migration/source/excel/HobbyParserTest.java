package org.encoway.migration.source.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.encoway.migration.model.Hobby;
import org.junit.jupiter.api.Test;

class HobbyParserTest {

    private final HobbyParser underTest = new HobbyParser();

    @Test
    void parseHobbies_assignsSequentialIds_skipsBlankValuesAndDuplicates() {
        // Arrange
        final var hobbyValues = " Hiking % 10% ; ; Hiking % 20% ; Reading % -5% ";

        // Act
        final var result = underTest.parseHobbies(hobbyValues, 7, 12);

        // Assert
        assertThat(result).containsExactly(
                new Hobby(12, 7, "Hiking", 10, HobbyParser.SOURCE_EXCEL),
                new Hobby(13, 7, "Reading", -5, HobbyParser.SOURCE_EXCEL));
    }

    @Test
    void parseHobbies_rejectsValuesWithoutPriorityMarkers() {
        // Arrange
        final var hobbyValues = "Hiking";

        // Act & Assert
        assertThatThrownBy(() -> underTest.parseHobbies(hobbyValues, 1, 1))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
    }
}
