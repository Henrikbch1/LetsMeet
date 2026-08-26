package org.encoway.persistence;

/**
 * Table names shared by {@link SchemaDefinition}, {@link DataInserter} and
 * {@code org.encoway.app.DatabaseMigrator}, kept in one place so creation, insertion and
 * removal always agree on the same name.
 */
public final class DatabaseObjectNames {

    public static final String TABLE_CITY = "city";
    public static final String TABLE_GENDER = "gender";
    public static final String TABLE_PERSON = "person";
    public static final String TABLE_HOBBY = "hobby";
    public static final String TABLE_PERSON_INTEREST = "person_interest";
    public static final String TABLE_PERSON_INTEREST_TEXT = "person_interest_text";
    public static final String TABLE_PERSON_LIKE = "person_like";
    public static final String TABLE_PERSON_MESSAGE = "person_message";

    private DatabaseObjectNames() {
    }
}
