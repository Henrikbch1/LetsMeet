package org.encoway;

import org.encoway.app.DatabaseMigrator;

public class Main {

    public static void main(String[] args) {

        new DatabaseMigrator().migrate();
    }
}