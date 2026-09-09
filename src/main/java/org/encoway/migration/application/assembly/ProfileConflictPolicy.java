package org.encoway.migration.application.assembly;

/**
 * Strategy for resolving profile field conflicts between the Excel source and the
 * MongoDB source during migration.
 */
public enum ProfileConflictPolicy {

    /**
     * Excel wins for contact/master fields; Mongo only supplements a field that is
     * blank in Excel. A nonblank Excel value is never overwritten by Mongo data.
     */
    EXCEL_WINS,

    /**
     * Mongo wins for the fields it supplies (first name, last name, phone); Excel only
     * supplements a field that is blank in Mongo. A nonblank Mongo value is never
     * overwritten by Excel data.
     */
    MONGO_WINS
}
