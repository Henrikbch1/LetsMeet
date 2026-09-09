package org.encoway;

import org.encoway.migration.application.MigrationRunner;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the transfer records path prefix as the only argument.");
        }
        new MigrationRunner().run(args[0]);
    }
}