package org.encoway.models;

/**
 * A raw interest code row for one person, such as {@code "m"}, {@code "w"} or
 * {@code "nb"}.
 */
public record RawInterest(Integer personId, String interestCode) {
}
