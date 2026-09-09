package org.encoway.migration.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationRejectionTest {

    @Test
    void constructor_acceptsNonBlankSourceSourceRefAndReason() {
        // Arrange & Act
        MigrationRejection underTest = new MigrationRejection("change-request.xml", "/transferpack/records/like[1]",
                "Orphan target email.");

        // Assert
        assertThat(underTest.source()).isEqualTo("change-request.xml");
        assertThat(underTest.sourceRef()).isEqualTo("/transferpack/records/like[1]");
        assertThat(underTest.reason()).isEqualTo("Orphan target email.");
    }

    @Test
    void constructor_rejectsBlankReason() {
        // Arrange, Act & Assert
        assertThatThrownBy(() -> new MigrationRejection("source.xml", "/ref[1]", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankSource() {
        // Arrange, Act & Assert
        assertThatThrownBy(() -> new MigrationRejection("", "/ref[1]", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankSourceRef() {
        // Arrange, Act & Assert
        assertThatThrownBy(() -> new MigrationRejection("source.xml", "", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
