package org.encoway.models;

import java.util.List;

public record MigrationData(
        List<City> cities,
        List<Gender> genders,
        List<Person> people,
        List<Hobby> hobbies,
        List<PersonInterest> personInterests
) {

    public MigrationData {
        cities = List.copyOf(cities);
        genders = List.copyOf(genders);
        people = List.copyOf(people);
        hobbies = List.copyOf(hobbies);
        personInterests = List.copyOf(personInterests);
    }
}
