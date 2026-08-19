package org.encoway;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.encoway.models.City;
import org.encoway.models.Gender;
import org.encoway.models.Hobby;
import org.encoway.models.MigrationData;
import org.encoway.models.Person;
import org.encoway.models.PersonInterest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExcelDataReader {

    private static final Path WORKBOOK_PATH = Path.of("Lets Meet DB Dump.xlsx");
    private static final int NAME_COLUMN = 0;
    private static final int ADDRESS_COLUMN = 1;
    private static final int PHONE_COLUMN = 2;
    private static final int HOBBIES_COLUMN = 3;
    private static final int EMAIL_COLUMN = 4;
    private static final int GENDER_COLUMN = 5;
    private static final int INTERESTS_COLUMN = 6;
    private static final int BIRTH_DATE_COLUMN = 7;

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<Gender> GENDERS = List.of(
            new Gender(1, "m"),
            new Gender(2, "w"),
            new Gender(3, "nb")
    );

    public MigrationData readMigrationData() {
        return readMigrationData(WORKBOOK_PATH);
    }

    public MigrationData readMigrationData(Path workbookPath) {
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            return mapWorkbook(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read workbook: " + workbookPath, exception);
        }
    }

    private MigrationData mapWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        DataFormatter formatter = new DataFormatter(Locale.GERMANY);
        List<Person> people = new ArrayList<>();
        List<Hobby> hobbies = new ArrayList<>();
        List<PersonInterest> personInterests = new ArrayList<>();
        Map<String, Map<String, Integer>> cityNamesByZipCode = new LinkedHashMap<>();
        int hobbyId = 1;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }

            int personId = people.size() + 1;
            Name name = splitName(cellText(row, NAME_COLUMN, formatter));
            Address address = splitAddress(cellText(row, ADDRESS_COLUMN, formatter));
            Person person = new Person(
                    personId,
                    name.lastName(),
                    name.firstName(),
                    address.street(),
                    address.streetNumber(),
                    address.zipCode(),
                    cellText(row, PHONE_COLUMN, formatter),
                    cellText(row, EMAIL_COLUMN, formatter),
                    genderId(cellText(row, GENDER_COLUMN, formatter)),
                    LocalDate.parse(cellText(row, BIRTH_DATE_COLUMN, formatter), BIRTH_DATE_FORMATTER)
            );

            people.add(person);
            List<Hobby> personHobbies = parseHobbies(
                    cellText(row, HOBBIES_COLUMN, formatter),
                    personId,
                    hobbyId
            );
            hobbies.addAll(personHobbies);
            hobbyId += personHobbies.size();
            personInterests.addAll(parseInterests(cellText(row, INTERESTS_COLUMN, formatter), personId));
            cityNamesByZipCode
                    .computeIfAbsent(address.zipCode(), ignored -> new LinkedHashMap<>())
                    .merge(address.cityName(), 1, Integer::sum);
        }

        return new MigrationData(
                toCities(cityNamesByZipCode),
                GENDERS,
                people,
                hobbies,
                personInterests
        );
    }

    private String cellText(Row row, int column, DataFormatter formatter) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).strip();
    }

    private Name splitName(String fullName) {
        String[] nameParts = fullName.split(",", 2);
        return new Name(nameParts[0].strip(), nameParts[1].strip());
    }

    private Address splitAddress(String fullAddress) {
        String[] addressParts = fullAddress.split(",", 3);
        StreetAddress streetAddress = splitStreetAddress(addressParts[0].strip());
        String zipCode = String.format("%05d", Integer.parseInt(addressParts[1].strip()));
        return new Address(streetAddress.street(), streetAddress.streetNumber(), zipCode, addressParts[2].strip());
    }

    private StreetAddress splitStreetAddress(String streetAndNumber) {
        int streetNumberStart = streetAndNumber.lastIndexOf(' ');
        if (Character.isLetter(streetAndNumber.charAt(streetNumberStart + 1))) {
            streetNumberStart = streetAndNumber.lastIndexOf(' ', streetNumberStart - 1);
        }
        return new StreetAddress(
                streetAndNumber.substring(0, streetNumberStart).strip(),
                streetAndNumber.substring(streetNumberStart + 1).strip()
        );
    }

    private int genderId(String gender) {
        return switch (gender) {
            case "m" -> 1;
            case "w" -> 2;
            case "nb" -> 3;
            default -> throw new IllegalArgumentException("Unknown gender: " + gender);
        };
    }

    private List<Hobby> parseHobbies(String hobbyValues, int personId, int firstHobbyId) {
        List<Hobby> hobbies = new ArrayList<>();
        int hobbyId = firstHobbyId;
        for (String hobbyValue : hobbyValues.split(";")) {
            if (hobbyValue.isBlank()) {
                continue;
            }

            int priorityStart = hobbyValue.lastIndexOf('%');
            int descriptionEnd = hobbyValue.lastIndexOf('%', priorityStart - 1);
            String description = hobbyValue.substring(0, descriptionEnd).strip();
            int priority = Integer.parseInt(hobbyValue.substring(descriptionEnd + 1, priorityStart).strip());
            hobbies.add(new Hobby(hobbyId++, personId, description, priority));
        }
        return hobbies;
    }

    private List<PersonInterest> parseInterests(String interests, int personId) {
        List<PersonInterest> personInterests = new ArrayList<>();
        if (interests.contains("m")) {
            personInterests.add(new PersonInterest(personId, 1));
        }
        if (interests.contains("w")) {
            personInterests.add(new PersonInterest(personId, 2));
        }
        if (interests.contains("nb")) {
            personInterests.add(new PersonInterest(personId, 3));
        }
        return personInterests;
    }

    private List<City> toCities(Map<String, Map<String, Integer>> cityNamesByZipCode) {
        return cityNamesByZipCode.entrySet().stream()
                .map(entry -> new City(entry.getKey(), mostCommonCityName(entry.getValue())))
                .toList();
    }

    private String mostCommonCityName(Map<String, Integer> cityNames) {
        return cityNames.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .orElseThrow()
                .getKey();
    }

    private record Name(String lastName, String firstName) {
    }

    private record Address(String street, String streetNumber, String zipCode, String cityName) {
    }

    private record StreetAddress(String street, String streetNumber) {
    }
}
