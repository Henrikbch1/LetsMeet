package org.encoway.model;

/**
 * Profile data read from a single MongoDB {@code users} document, before it is merged
 * with the corresponding Excel {@link Person}.
 */
public record MongoProfile(String email, String firstName, String lastName, String phone) {
}
