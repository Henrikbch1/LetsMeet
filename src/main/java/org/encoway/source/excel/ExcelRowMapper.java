package org.encoway.source.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.encoway.model.City;
import org.encoway.model.Gender;
import org.encoway.model.Hobby;
import org.encoway.model.MigrationData;
import org.encoway.model.Person;
import org.encoway.model.PersonInterest;
import org.encoway.model.RawInterest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class ExcelRowMapper {

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

    private final DataFormatter formatter = new DataFormatter(Locale.GERMANY);
    private final List<Person> people = new ArrayList<>();
    private final List<Hobby> hobbies = new ArrayList<>();
    private final List<PersonInterest> personInterests = new ArrayList<>();
    private final List<RawInterest> rawInterests = new ArrayList<>();
    private final Map<CityAddress, City> citiesByAddress = new LinkedHashMap<>();
    private int hobbyId = 1;

    void mapRow(Row row) {
        int personId = people.size() + 1;
        people.add(mapPerson(row, personId));
        mapHobbies(row, personId);
        mapInterests(row, personId);
    }

    MigrationData toMigrationData() {
        return new MigrationData(
                List.copyOf(citiesByAddress.values()),
                GENDERS,
                people,
                hobbies,
                personInterests,
                rawInterests,
                List.of(),
                List.of()
        );
    }

    private Person mapPerson(Row row, int personId) {
        Name name = splitName(cellText(row, NAME_COLUMN));
        Address address = splitAddress(cellText(row, ADDRESS_COLUMN));
        City city = resolveCity(address);
        return new Person(
                personId,
                name.lastName(),
                name.firstName(),
                address.street(),
                address.streetNumber(),
                city.cityId(),
                cellText(row, PHONE_COLUMN),
                cellText(row, EMAIL_COLUMN),
                genderCodeToId(cellText(row, GENDER_COLUMN), "gender"),
                LocalDate.parse(cellText(row, BIRTH_DATE_COLUMN), BIRTH_DATE_FORMATTER)
        );
    }

    private City resolveCity(Address address) {
        return citiesByAddress.computeIfAbsent(
                new CityAddress(address.zipCode(), address.cityName()),
                cityAddress -> new City(
                        citiesByAddress.size() + 1,
                        cityAddress.zipCode(),
                        cityAddress.cityName()
                )
        );
    }

    private void mapHobbies(Row row, int personId) {
        List<Hobby> personHobbies = parseHobbies(
                cellText(row, HOBBIES_COLUMN),
                personId,
                hobbyId
        );
        hobbies.addAll(personHobbies);
        hobbyId += personHobbies.size();
    }

    private void mapInterests(Row row, int personId) {
        String interestsText = cellText(row, INTERESTS_COLUMN);
        for (String interestCode : interestCodes(interestsText)) {
            rawInterests.add(new RawInterest(personId, interestCode));
            personInterests.add(new PersonInterest(personId, genderCodeToId(interestCode, "interest")));
        }
    }

    private String cellText(Row row, int column) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private Name splitName(String fullName) {
        int separatorIndex = fullName.indexOf(", ");
        return new Name(
                fullName.substring(0, separatorIndex),
                fullName.substring(separatorIndex + 2)
        );
    }

    private Address splitAddress(String fullAddress) {
        int firstSeparator = fullAddress.indexOf(", ");
        int secondSeparator = fullAddress.indexOf(", ", firstSeparator + 2);
        StreetAddress streetAddress = splitStreetAddress(fullAddress.substring(0, firstSeparator));
        return new Address(
                streetAddress.street(),
                streetAddress.streetNumber(),
                fullAddress.substring(firstSeparator + 2, secondSeparator),
                fullAddress.substring(secondSeparator + 2)
        );
    }

    private StreetAddress splitStreetAddress(String streetAndNumber) {
        int streetNumberStart = streetAndNumber.lastIndexOf(' ');
        if (Character.isLetter(streetAndNumber.charAt(streetNumberStart + 1))) {
            streetNumberStart = streetAndNumber.lastIndexOf(' ', streetNumberStart - 1);
        }
        return new StreetAddress(
                streetAndNumber.substring(0, streetNumberStart),
                streetAndNumber.substring(streetNumberStart + 1)
        );
    }

    private int genderCodeToId(String genderCode, String valueType) {
        return switch (genderCode) {
            case "m" -> 1;
            case "w" -> 2;
            case "nb" -> 3;
            default -> throw new IllegalArgumentException("Unknown " + valueType + ": " + genderCode);
        };
    }

    private List<String> interestCodes(String interestValue) {
        if (interestValue.isBlank()) {
            return List.of();
        }
        return switch (interestValue) {
            case "m" -> List.of("m");
            case "w" -> List.of("w");
            case "nb" -> List.of("nb");
            case "mw" -> List.of("m", "w");
            default -> throw new IllegalArgumentException("Unknown interest: " + interestValue);
        };
    }

    private List<Hobby> parseHobbies(String hobbyValues, int personId, int firstHobbyId) {
        List<Hobby> hobbies = new ArrayList<>();
        Set<String> seenDescriptions = new LinkedHashSet<>();
        int hobbyId = firstHobbyId;
        for (String hobbyValue : hobbyValues.split(";")) {
            if (hobbyValue.isBlank()) {
                continue;
            }

            int priorityStart = hobbyValue.lastIndexOf('%');
            int descriptionEnd = hobbyValue.lastIndexOf('%', priorityStart - 1);
            String description = hobbyValue.substring(0, descriptionEnd).strip();
            int priority = Integer.parseInt(hobbyValue.substring(descriptionEnd + 1, priorityStart).strip());
            if (!seenDescriptions.add(description)) {
                // Same hobby fact for this person/source already recorded; keep only the first occurrence.
                continue;
            }
            hobbies.add(new Hobby(hobbyId++, personId, description, priority));
        }
        return hobbies;
    }

    private record Name(String lastName, String firstName) {
    }

    private record Address(String street, String streetNumber, String zipCode, String cityName) {
    }

    private record StreetAddress(String street, String streetNumber) {
    }

    private record CityAddress(String zipCode, String cityName) {
    }
}
