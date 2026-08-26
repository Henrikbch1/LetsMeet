package org.encoway.model;

/**
 * A raw interest code row for one person, such as {@code "m"}, {@code "w"} or
 * {@code "nb"}.
 */
public record RawInterest(Integer personId, String interestCode) {
}
