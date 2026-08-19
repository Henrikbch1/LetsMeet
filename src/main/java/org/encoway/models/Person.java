package org.encoway.models;

import java.time.LocalDate;

public record Person(
        Integer personId,
        String lastName,
        String firstName,
        String street,
        String streetNumber,
        String zipCode,
        String phoneNumber,
        String email,
        Integer genderId,
        LocalDate birthDate
) {

}
