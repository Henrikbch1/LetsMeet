package org.encoway.migration.source.transfer;

import org.encoway.migration.model.City;
import org.encoway.migration.model.Hobby;
import org.encoway.migration.model.MigrationData;
import org.encoway.migration.model.MigrationRejection;
import org.encoway.migration.model.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TransferPackageProcessorTest {

    private static final String CHANGE_REQUEST_FILE = "change-request.xml";
    private static final String ENCODING_INVALID_FILE = "encoding-invalid.xml";
    private static final String ENCODING_MOJIBAKE_FILE = "encoding-mojibake.xml";

    private final TransferPackageProcessor underTest = new TransferPackageProcessor();

    @Test
    void process_appliesTheFullEightRecordTransferV3Matrix(@TempDir Path packageDir) throws IOException {
        // Arrange
        writeXml(packageDir, CHANGE_REQUEST_FILE, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack>
                  <records>
                    <like email="abdel.sabah@1mal1.te" target_email="transfer.orphan@letsmeet.invalid"/>
                    <hobby email="abdel.sabah@1mal1.te" name="E-Mails schreiben"/>
                    <hobby email="abdulk..stuckmann@web.kom" name="Abends erzaehlen" priority="101"/>
                    <profile email="transfer.p2@letsmeet.invalid" first_name="Pia" last_name="Transfer" birth_date="01.01.1900" postal_code="69115" city="unbekannt" phone="+49 6221 123456" gender="w"/>
                    <hobby email="acar.nehir@ge-em-ix.kom" name="Abends erzaehlen"/>
                    <hobby email="acar.nehir@ge-em-ix.kom" name="Abends erzaehlen"/>
                  </records>
                </transferpack>
                """);
        writeInvalidEncodingProfile(packageDir);
        writeMojibakeProfile(packageDir);

        MigrationData migrationData = new MigrationData(
                List.of(),
                List.of(),
                List.of(
                        person(1, "abdel.sabah@1mal1.te"),
                        person(2, "abdulk..stuckmann@web.kom"),
                        person(3, "acar.nehir@ge-em-ix.kom")),
                List.of(new Hobby(1, 1, "E-Mails schreiben", 14, "excel")),
                List.of(), List.of(), List.of(), List.of());

        // Act
        TransferPackageProcessor.Result result = underTest.process(packageDir, migrationData);

        // Assert
        assertThat(result.pendingHobbies())
                .extracting(TransferPackageProcessor.PendingHobby::source,
                        TransferPackageProcessor.PendingHobby::sourceRef,
                        TransferPackageProcessor.PendingHobby::priority)
                .containsExactly(
                        tuple(CHANGE_REQUEST_FILE, "/transferpack/records/hobby[3]", null));

        assertThat(result.pendingProfiles())
                .extracting(TransferPackageProcessor.PendingProfile::source,
                        TransferPackageProcessor.PendingProfile::sourceRef)
                .containsExactlyInAnyOrder(
                        tuple(ENCODING_MOJIBAKE_FILE, "/transferpack/records/profile[1]"));
        assertThat(result.pendingProfiles())
                .filteredOn(this::isMojibakeProfile)
                .extracting(TransferPackageProcessor.PendingProfile::person)
                .extracting(Person::firstName)
                .containsExactly("Müller");

        assertThat(result.rejections())
                .extracting(MigrationRejection::source, MigrationRejection::sourceRef)
                .containsExactlyInAnyOrder(
                        tuple(CHANGE_REQUEST_FILE, "/transferpack/records/like[1]"),
                        tuple(CHANGE_REQUEST_FILE, "/transferpack/records/hobby[1]"),
                        tuple(CHANGE_REQUEST_FILE, "/transferpack/records/hobby[2]"),
                        tuple(CHANGE_REQUEST_FILE, "/transferpack/records/hobby[4]"),
                        tuple(CHANGE_REQUEST_FILE, "/transferpack/records/profile[1]"),
                        tuple(ENCODING_INVALID_FILE, "/transferpack/records/profile[1]"));
        assertThat(result.rejections()).allSatisfy(this::assertReasonIsNotBlank);
    }

    @Test
    void process_hobbyPriorityBoundaries_minusHundredAndHundredAccepted_101Rejected(@TempDir Path packageDir)
            throws IOException {
        // Arrange
        writeXml(packageDir, CHANGE_REQUEST_FILE, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack>
                  <records>
                    <hobby email="a@example.com" name="Hobby A" priority="-100"/>
                    <hobby email="a@example.com" name="Hobby B" priority="100"/>
                    <hobby email="a@example.com" name="Hobby C" priority="101"/>
                  </records>
                </transferpack>
                """);
        writeEmptyProfileFile(packageDir, ENCODING_INVALID_FILE);
        writeEmptyProfileFile(packageDir, ENCODING_MOJIBAKE_FILE);
        MigrationData migrationData = new MigrationData(
                List.of(), List.of(), List.of(person(1, "a@example.com")), List.of(), List.of(), List.of(), List.of(),
                List.of());

        // Act
        TransferPackageProcessor.Result result = underTest.process(packageDir, migrationData);

        // Assert
        assertThat(result.pendingHobbies())
                .extracting(TransferPackageProcessor.PendingHobby::priority)
                .containsExactly(-100, 100);
        assertThat(result.rejections())
                .extracting(MigrationRejection::sourceRef)
                .containsExactly("/transferpack/records/hobby[3]");
    }

    @Test
    void process_encodingInvalidProfile_isRejectedForReplacementCharacter(@TempDir Path packageDir)
            throws IOException {
        // Arrange
        writeEmptyRecordsFile(packageDir, CHANGE_REQUEST_FILE);
        writeXml(packageDir, ENCODING_INVALID_FILE, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack><records><profile email="broken@letsmeet.invalid" first_name="Andr\uFFFD" last_name="Byte" birth_date="1988-04-12" postal_code="50667" city="Koeln" phone="+49 221 123456" gender="m"/></records></transferpack>
                """);
        writeEmptyProfileFile(packageDir, ENCODING_MOJIBAKE_FILE);
        MigrationData migrationData = new MigrationData(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        // Act
        TransferPackageProcessor.Result result = underTest.process(packageDir, migrationData);

        // Assert
        assertThat(result.pendingProfiles()).isEmpty();
        assertThat(result.rejections()).hasSize(1);
        assertThat(result.rejections().get(0).source()).isEqualTo(ENCODING_INVALID_FILE);
        assertThat(result.rejections().get(0).sourceRef()).isEqualTo("/transferpack/records/profile[1]");
        assertThat(result.rejections().get(0).reason()).isNotBlank();
    }

    @Test
    void process_mojibakeProfile_repairsOnlyTheAffectedField_andIsAccepted(@TempDir Path packageDir)
            throws IOException {
        // Arrange
        writeEmptyRecordsFile(packageDir, CHANGE_REQUEST_FILE);
        writeEmptyProfileFile(packageDir, ENCODING_INVALID_FILE);
        writeXml(packageDir, ENCODING_MOJIBAKE_FILE, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack><records><profile email="mojibake@letsmeet.invalid" first_name="MÃ¼ller" last_name="Text" birth_date="1988-04-12" postal_code="50667" city="Köln" phone="+49 221 123457" gender="w"/></records></transferpack>
                """);
        MigrationData migrationData = new MigrationData(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        // Act
        TransferPackageProcessor.Result result = underTest.process(packageDir, migrationData);

        // Assert
        assertThat(result.rejections()).isEmpty();
        assertThat(result.pendingProfiles()).hasSize(1);
        Person person = result.pendingProfiles().get(0).person();
        assertThat(person.firstName()).isEqualTo("Müller");
        City city = result.pendingProfiles().get(0).city();
        assertThat(city.cityName()).isEqualTo("Köln");
    }

    @Test
    void process_reusesNewCityForMultipleProfiles(@TempDir Path packageDir) throws IOException {
        writeXml(packageDir, CHANGE_REQUEST_FILE, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack><records>
                  <profile email="first@letsmeet.invalid" first_name="First" last_name="Profile" birth_date="1988-04-12" postal_code="12345" city="New City" phone="+49 1" gender="m"/>
                  <profile email="second@letsmeet.invalid" first_name="Second" last_name="Profile" birth_date="1988-04-12" postal_code="12345" city="New City" phone="+49 2" gender="w"/>
                </records></transferpack>
                """);
        writeEmptyProfileFile(packageDir, ENCODING_INVALID_FILE);
        writeEmptyProfileFile(packageDir, ENCODING_MOJIBAKE_FILE);
        MigrationData migrationData = new MigrationData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        TransferPackageProcessor.Result result = underTest.process(packageDir, migrationData);

        assertThat(result.rejections()).isEmpty();
        assertThat(result.pendingProfiles()).extracting(TransferPackageProcessor.PendingProfile::newCity)
                .containsExactly(true, false);
        assertThat(result.pendingProfiles())
                .extracting(TransferPackageProcessor.PendingProfile::city)
                .extracting(City::cityId)
                .containsExactly(1, 1);
    }

    private boolean isMojibakeProfile(TransferPackageProcessor.PendingProfile profile) {
        return profile.source().equals(ENCODING_MOJIBAKE_FILE);
    }

    private void assertReasonIsNotBlank(MigrationRejection rejection) {
        assertThat(rejection.reason()).isNotBlank();
    }

    private void writeInvalidEncodingProfile(Path packageDir) throws IOException {
        // Genuinely malformed UTF-8 (a lone 0xE9 byte, not a valid continuation), so the lenient
        // decode must turn it into a U+FFFD replacement character.
        byte[] prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><transferpack><records><profile email=\"transfer.bytes@letsmeet.invalid\" first_name=\"Andr"
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "\" last_name=\"Byte\" birth_date=\"1988-04-12\" postal_code=\"50667\" city=\"Koeln\" phone=\"+49 221 123456\" gender=\"m\"/></records></transferpack>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[prefix.length + 1 + suffix.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        content[prefix.length] = (byte) 0xE9;
        System.arraycopy(suffix, 0, content, prefix.length + 1, suffix.length);
        Files.write(packageDir.resolve(ENCODING_INVALID_FILE), content);
    }

    private void writeMojibakeProfile(Path packageDir) throws IOException {
        writeXml(packageDir, ENCODING_MOJIBAKE_FILE, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack><records><profile email="transfer.mojibake@letsmeet.invalid" first_name="MÃ¼ller" last_name="Text" birth_date="1988-04-12" postal_code="50667" city="Köln" phone="+49 221 123457" gender="w"/></records></transferpack>
                """);
    }

    private void writeEmptyRecordsFile(Path packageDir, String fileName) throws IOException {
        writeXml(packageDir, fileName, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack><records></records></transferpack>
                """);
    }

    private void writeEmptyProfileFile(Path packageDir, String fileName) throws IOException {
        writeXml(packageDir, fileName, """
                <?xml version="1.0" encoding="UTF-8"?>
                <transferpack><records></records></transferpack>
                """);
    }

    private void writeXml(Path packageDir, String fileName, String content) throws IOException {
        Files.writeString(packageDir.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private Person person(int id, String email) {
        return new Person(id, "Last", "First", "Street", "1", null, "000", email, 1, LocalDate.of(2000, 1, 1));
    }
}
