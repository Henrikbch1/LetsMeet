package org.encoway.models;

public record Person(
        Integer personId,
        String lastName,
        String firstName,
        String street,
        String zipCode,
        String phoneNumber,
        String email,
        Integer genderId,
        String birthDate
) {

}
