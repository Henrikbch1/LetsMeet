package org.encoway.migration.model;

/**
 * A single rejected physical record from a data delivery, kept for auditability. The
 * combination of {@code source} (the delivering file name) and {@code sourceRef} (the
 * record's path within that file, e.g. {@code /transferpack/records/hobby[2]}) identifies
 * the record uniquely; {@code reason} is a short, nonblank explanation.
 */
public record MigrationRejection(String source, String sourceRef, String reason) {

    public MigrationRejection {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Rejection source must not be blank.");
        }
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException("Rejection source reference must not be blank.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank.");
        }
    }
}
