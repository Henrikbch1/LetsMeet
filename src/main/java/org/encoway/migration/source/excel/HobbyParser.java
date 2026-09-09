package org.encoway.migration.source.excel;

import org.encoway.migration.model.Hobby;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HobbyParser {

    public static final String SOURCE_EXCEL = "excel";

    private static final String HOBBY_DELIMITER = ";";
    private static final char PRIORITY_MARKER = '%';

    public List<Hobby> parseHobbies(String hobbyValues, int personId, int firstHobbyId) {
        List<Hobby> hobbies = new ArrayList<>();
        Set<String> seenDescriptions = new LinkedHashSet<>();
        int hobbyId = firstHobbyId;
        for (String hobbyValue : hobbyValues.split(HOBBY_DELIMITER)) {
            if (!hobbyValue.isBlank()) {
                int priorityStart = hobbyValue.lastIndexOf(PRIORITY_MARKER);
                int descriptionEnd = hobbyValue.lastIndexOf(PRIORITY_MARKER, priorityStart - 1);
                String description = hobbyValue.substring(0, descriptionEnd).strip();
                int priority = Integer.parseInt(
                        hobbyValue.substring(descriptionEnd + 1, priorityStart).strip());
                if (seenDescriptions.add(description)) {
                    hobbies.add(new Hobby(hobbyId++, personId, description, priority, SOURCE_EXCEL));
                }
            }
        }
        return hobbies;
    }
}
