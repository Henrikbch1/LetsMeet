package org.encoway.source.excel;

import org.encoway.model.Hobby;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class HobbyParser {

    List<Hobby> parseHobbies(String hobbyValues, int personId, int firstHobbyId) {
        List<Hobby> hobbies = new ArrayList<>();
        Set<String> seenDescriptions = new LinkedHashSet<>();
        int hobbyId = firstHobbyId;
        for (String hobbyValue : hobbyValues.split(";")) {
            if (hobbyValue.isBlank()) {
                continue;
            }

            int priorityStart = hobbyValue.lastIndexOf('%');
            int descriptionEnd = hobbyValue.lastIndexOf('%', priorityStart - 1);
            String description = hobbyValue.substring(0, descriptionEnd).strip();
            int priority = Integer.parseInt(hobbyValue.substring(descriptionEnd + 1, priorityStart).strip());
            if (!seenDescriptions.add(description)) {
                // Same hobby fact for this person/source already recorded; keep only the first occurrence.
                continue;
            }
            hobbies.add(new Hobby(hobbyId++, personId, description, priority));
        }
        return hobbies;
    }
}
