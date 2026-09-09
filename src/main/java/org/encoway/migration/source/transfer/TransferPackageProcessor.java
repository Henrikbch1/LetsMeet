package org.encoway.migration.source.transfer;

import org.encoway.migration.domain.model.City;
import org.encoway.migration.domain.model.Hobby;
import org.encoway.migration.domain.model.MigrationData;
import org.encoway.migration.domain.model.MigrationRejection;
import org.encoway.migration.domain.model.Person;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and validates the {@code letsmeet-transfer-v3} delivery package: the change requests
 * (likes, hobbies, profiles), plus the two single-profile encoding regression files. Each of
 * the eight physical records is validated independently, so that one rejected record never
 * prevents the remaining records of the same file/delivery from being processed. Records that
 * pass validation are returned as pending inserts for the caller to apply (with per-record
 * fault isolation at the database level); records that fail are returned as
 * {@link MigrationRejection}s, identified by the delivering file name and the record's path,
 * exactly as listed in {@code manifest.json}.
 */
public class TransferPackageProcessor {

    private static final Path DEFAULT_DIRECTORY = Path.of("letsmeet-transfer-v3");

    private static final String CHANGE_REQUEST_FILE = "change-request.xml";
    private static final String ENCODING_INVALID_FILE = "encoding-invalid.xml";
    private static final String ENCODING_MOJIBAKE_FILE = "encoding-mojibake.xml";

    private static final String RECORDS_TAG = "records";
    private static final String LIKE_TAG = "like";
    private static final String HOBBY_TAG = "hobby";
    private static final String PROFILE_TAG = "profile";
    private static final String ATTRIBUTE_EMAIL = "email";
    private static final String ATTRIBUTE_TARGET_EMAIL = "target_email";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_PRIORITY = "priority";
    private static final String ATTRIBUTE_FIRST_NAME = "first_name";
    private static final String ATTRIBUTE_LAST_NAME = "last_name";
    private static final String ATTRIBUTE_BIRTH_DATE = "birth_date";
    private static final String ATTRIBUTE_POSTAL_CODE = "postal_code";
    private static final String ATTRIBUTE_CITY = "city";
    private static final String ATTRIBUTE_PHONE = "phone";
    private static final String ATTRIBUTE_GENDER = "gender";
    private static final LocalDate SENTINEL_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final String SENTINEL_CITY = "unbekannt";

    private static final String GENDER_CODE_MALE = "m";
    private static final String GENDER_CODE_FEMALE = "w";
    private static final String GENDER_CODE_NON_BINARY = "nb";
    private static final int GENDER_ID_MALE = 1;
    private static final int GENDER_ID_FEMALE = 2;
    private static final int GENDER_ID_NON_BINARY = 3;

    private static final int HOBBY_PRIORITY_MIN = -100;
    private static final int HOBBY_PRIORITY_MAX = 100;

    // Hobbies delivered outside of the Excel workbook (root XML or transfer package) are all
    // tagged with this source, regardless of which XML file they came from.
    private static final String SOURCE_XML = "xml";

    private static final char REPLACEMENT_CHARACTER = '\uFFFD';
    // Strong signal of a UTF-8 byte sequence that was mistakenly re-decoded as ISO-8859-1/Windows-1252.
    private static final char MOJIBAKE_MARKER = '\u00C3';

    private static final DateTimeFormatter GERMAN_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String recordsPathPrefix;

    /**
     * Creates a processor that uses the supplied path prefix for transfer record references.
     *
     * @param recordsPathPrefix the path prefix for transfer record references
     */
    public TransferPackageProcessor(String recordsPathPrefix) {
        this.recordsPathPrefix = Objects.requireNonNull(recordsPathPrefix, "recordsPathPrefix");
    }

    public Result process(MigrationData migrationData) {
        return process(DEFAULT_DIRECTORY, migrationData);
    }

    public Result process(Path packageDirectory, MigrationData migrationData) {
        Context context = new Context(
                indexPeople(migrationData.people()),
                indexCities(migrationData.cities()),
                maxPersonId(migrationData.people()) + 1,
                maxCityId(migrationData.cities()) + 1,
                maxHobbyId(migrationData.hobbies()) + 1,
                indexExistingHobbyFacts(migrationData.hobbies()));

        processChangeRequestFile(packageDirectory.resolve(CHANGE_REQUEST_FILE), context);
        processSingleProfileFile(packageDirectory.resolve(ENCODING_INVALID_FILE), ENCODING_INVALID_FILE, context);
        processSingleProfileFile(packageDirectory.resolve(ENCODING_MOJIBAKE_FILE), ENCODING_MOJIBAKE_FILE, context);

        return new Result(context.rejections, context.pendingHobbies, context.pendingProfiles);
    }

    private void processChangeRequestFile(Path filePath, Context context) {
        Document document = parseSecurely(filePath);
        int likeCounter = 0;
        int hobbyCounter = 0;
        int profileCounter = 0;
        for (Element element : directChildElements(document, RECORDS_TAG)) {
            switch (element.getTagName()) {
                case LIKE_TAG -> {
                    likeCounter++;
                    handleLike(element, CHANGE_REQUEST_FILE, recordPath(LIKE_TAG, likeCounter), context);
                }
                case HOBBY_TAG -> {
                    hobbyCounter++;
                    handleHobby(element, CHANGE_REQUEST_FILE, recordPath(HOBBY_TAG, hobbyCounter), context);
                }
                case PROFILE_TAG -> {
                    profileCounter++;
                    handleProfile(element, CHANGE_REQUEST_FILE, recordPath(PROFILE_TAG, profileCounter), context);
                }
                default -> throw new IllegalStateException(
                        "Unknown transfer record tag: " + element.getTagName());
            }
        }
    }

    private void processSingleProfileFile(Path filePath, String sourceFileName, Context context) {
        Document document = parseSecurely(filePath);
        NodeList profileNodes = document.getElementsByTagName(PROFILE_TAG);
        for (int i = 0; i < profileNodes.getLength(); i++) {
            handleProfile((Element) profileNodes.item(i), sourceFileName, recordPath(PROFILE_TAG, i + 1), context);
        }
    }

    private void handleLike(Element element, String sourceFileName, String sourceRef, Context context) {
        String email = element.getAttribute(ATTRIBUTE_EMAIL);
        String targetEmail = element.getAttribute(ATTRIBUTE_TARGET_EMAIL);
        Integer likerId = context.personIdByLowerEmail.get(email.toLowerCase(Locale.ROOT));
        Integer likedId = context.personIdByLowerEmail.get(targetEmail.toLowerCase(Locale.ROOT));
        if (likerId == null || likedId == null) {
            context.rejections.add(new MigrationRejection(
                    sourceFileName, sourceRef, "Like references an unresolved person email (orphan target)."));
            return;
        }
        // Both parties resolve, but the transfer schema carries no status/timestamp, so a real
        // person_like row cannot be constructed without inventing data.
        context.rejections.add(new MigrationRejection(
                sourceFileName, sourceRef, "Like is missing the required status/timestamp fields."));
    }

    private void handleHobby(Element element, String sourceFileName, String sourceRef, Context context) {
        String email = element.getAttribute(ATTRIBUTE_EMAIL);
        String description = element.getAttribute(ATTRIBUTE_NAME).strip();
        Integer personId = context.personIdByLowerEmail.get(email.toLowerCase(Locale.ROOT));
        if (personId == null) {
            context.rejections.add(new MigrationRejection(
                    sourceFileName, sourceRef, "No matching person found for the hobby's email."));
            return;
        }
        Integer priority = null;
        if (element.hasAttribute(ATTRIBUTE_PRIORITY)) {
            priority = Integer.parseInt(element.getAttribute(ATTRIBUTE_PRIORITY).strip());
        }
        if (priority != null && (priority < HOBBY_PRIORITY_MIN || priority > HOBBY_PRIORITY_MAX)) {
            context.rejections.add(new MigrationRejection(sourceFileName, sourceRef,
                    "Priority " + priority + " is outside the allowed range " + HOBBY_PRIORITY_MIN
                            + ".." + HOBBY_PRIORITY_MAX + "."));
            return;
        }
        PersonHobbyKey key = new PersonHobbyKey(personId, description);
        if (!context.inPackageHobbyFacts.add(key)) {
            context.rejections.add(new MigrationRejection(
                    sourceFileName, sourceRef, "Duplicate hobby fact already accepted earlier in this delivery."));
            return;
        }
        context.pendingHobbies.add(new PendingHobby(
                sourceFileName, sourceRef, context.nextHobbyId.next(), personId, description, priority, SOURCE_XML));
    }

    private void handleProfile(Element element, String sourceFileName, String sourceRef, Context context) {
        String email = element.getAttribute(ATTRIBUTE_EMAIL);
        String firstName = element.getAttribute(ATTRIBUTE_FIRST_NAME);
        String lastName = element.getAttribute(ATTRIBUTE_LAST_NAME);
        String birthDateText = element.getAttribute(ATTRIBUTE_BIRTH_DATE);
        String postalCode = element.getAttribute(ATTRIBUTE_POSTAL_CODE);
        String cityName = element.getAttribute(ATTRIBUTE_CITY);
        String phone = element.getAttribute(ATTRIBUTE_PHONE);
        String genderCode = element.getAttribute(ATTRIBUTE_GENDER);

        if (containsReplacementCharacter(email, firstName, lastName, birthDateText, postalCode, cityName, phone,
                genderCode)) {
            context.rejections.add(new MigrationRejection(sourceFileName, sourceRef,
                    "Profile contains an unrecoverable encoding artifact (U+FFFD) in a text field."));
            return;
        }

        firstName = repairMojibake(firstName);
        lastName = repairMojibake(lastName);
        cityName = repairMojibake(cityName);

        LocalDate birthDate;
        try {
            birthDate = parseDate(birthDateText);
        } catch (DateTimeParseException exception) {
            context.rejections.add(new MigrationRejection(
                    sourceFileName, sourceRef, "Profile has an unparsable birth date."));
            return;
        }

        if (SENTINEL_BIRTH_DATE.equals(birthDate) && SENTINEL_CITY.equals(cityName)) {
            context.rejections.add(new MigrationRejection(
                    sourceFileName, sourceRef, "Profile contains sentinel birth date and city values."));
            return;
        }

        Integer genderId = genderCodeToId(genderCode);
        if (genderId == null) {
            context.rejections.add(new MigrationRejection(
                    sourceFileName, sourceRef, "Profile has an unknown gender code."));
            return;
        }

        CityKey cityKey = new CityKey(postalCode, cityName);
        boolean newCity = !context.citiesByKey.containsKey(cityKey);
        City city = resolveOrCreateCity(postalCode, cityName, context);
        Person person = new Person(context.nextPersonId.next(), lastName, firstName, null, null, city.cityId(),
                phone, email, genderId, birthDate);
        context.pendingProfiles.add(new PendingProfile(sourceFileName, sourceRef, person, city, newCity));
    }

    private City resolveOrCreateCity(String zipCode, String cityName, Context context) {
        CityKey key = new CityKey(zipCode, cityName);
        City existing = context.citiesByKey.get(key);
        if (existing != null) {
            return existing;
        }
        City created = new City(context.nextCityId.next(), zipCode, cityName);
        context.citiesByKey.put(key, created);
        return created;
    }

    private boolean containsReplacementCharacter(String... values) {
        for (String value : values) {
            if (value != null && value.indexOf(REPLACEMENT_CHARACTER) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Repairs the classic mojibake pattern where a UTF-8 encoded value was mistakenly re-decoded
     * as ISO-8859-1/Windows-1252 (e.g. {@code "MÃ¼ller"} instead of {@code "Müller"}). Only
     * applied to values that actually show the mojibake marker, and only kept if re-decoding
     * succeeds without introducing replacement characters, so unaffected values (e.g. correctly
     * encoded {@code "Köln"}) are left untouched.
     */
    private String repairMojibake(String value) {
        if (value == null || value.indexOf(MOJIBAKE_MARKER) < 0) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return repaired.indexOf(REPLACEMENT_CHARACTER) < 0 ? repaired : value;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, GERMAN_DATE_FORMAT);
        } catch (DateTimeParseException germanException) {
            try {
                return LocalDate.parse(text, ISO_DATE_FORMAT);
            } catch (DateTimeParseException isoException) {
                isoException.addSuppressed(germanException);
                throw isoException;
            }
        }
    }

    private Integer genderCodeToId(String genderCode) {
        return switch (genderCode) {
            case GENDER_CODE_MALE -> GENDER_ID_MALE;
            case GENDER_CODE_FEMALE -> GENDER_ID_FEMALE;
            case GENDER_CODE_NON_BINARY -> GENDER_ID_NON_BINARY;
            default -> null;
        };
    }

    private String recordPath(String tagName, int index) {
        return recordsPathPrefix + tagName + "[" + index + "]";
    }

    private List<Element> directChildElements(Document document, String parentTagName) {
        NodeList parentNodes = document.getElementsByTagName(parentTagName);
        if (parentNodes.getLength() == 0) {
            return List.of();
        }
        List<Element> children = new ArrayList<>();
        NodeList childNodes = parentNodes.item(0).getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private Map<String, Integer> indexPeople(List<Person> people) {
        Map<String, Integer> personIdByLowerEmail = new HashMap<>();
        for (Person person : people) {
            personIdByLowerEmail.put(person.email().toLowerCase(Locale.ROOT), person.personId());
        }
        return personIdByLowerEmail;
    }

    private Map<CityKey, City> indexCities(List<City> cities) {
        Map<CityKey, City> citiesByKey = new HashMap<>();
        for (City city : cities) {
            citiesByKey.put(new CityKey(city.zipCode(), city.cityName()), city);
        }
        return citiesByKey;
    }

    private int maxPersonId(List<Person> people) {
        int maxId = 0;
        for (Person person : people) {
            maxId = Math.max(maxId, person.personId());
        }
        return maxId;
    }

    private int maxCityId(List<City> cities) {
        int maxId = 0;
        for (City city : cities) {
            maxId = Math.max(maxId, city.cityId());
        }
        return maxId;
    }

    private int maxHobbyId(List<Hobby> hobbies) {
        int maxId = 0;
        for (Hobby hobby : hobbies) {
            maxId = Math.max(maxId, hobby.hobbyId());
        }
        return maxId;
    }

    /**
     * Indexes already imported hobby facts (from Excel or the root XML) by person/description,
     * independent of {@code source}, so a transfer package record describing the same fact is
     * recognized as a duplicate rather than re-imported under a different source.
     */
    private Set<PersonHobbyKey> indexExistingHobbyFacts(List<Hobby> hobbies) {
        Set<PersonHobbyKey> facts = new HashSet<>();
        for (Hobby hobby : hobbies) {
            facts.add(new PersonHobbyKey(hobby.userId(), hobby.description()));
        }
        return facts;
    }

    /**
     * The outcome of processing the transfer package: immediate rejections plus the candidate
     * hobby/profile records that passed validation and are ready to be attempted for insertion.
     * Each pending insert still needs to be attempted individually (e.g. with a savepoint) by the
     * caller, since a database-level fault (such as a real duplicate) must also become a rejection.
     */
    public record Result(
            List<MigrationRejection> rejections,
            List<PendingHobby> pendingHobbies,
            List<PendingProfile> pendingProfiles) {
    }

    public record PendingHobby(
            String source,
            String sourceRef,
            Integer hobbyId,
            Integer personId,
            String description,
            Integer priority,
            String hobbySource) {

        public Hobby toHobby() {
            return new Hobby(hobbyId, personId, description, priority, hobbySource);
        }
    }

    public record PendingProfile(String source, String sourceRef, Person person, City city, boolean newCity) {
    }

    private record PersonHobbyKey(Integer personId, String description) {
    }

    private record CityKey(String zipCode, String cityName) {
    }

    private static final class Counter {
        private int value;

        private Counter(int initialValue) {
            this.value = initialValue;
        }

        private int next() {
            return value++;
        }
    }

    private static final class Context {
        private final Map<String, Integer> personIdByLowerEmail;
        private final Map<CityKey, City> citiesByKey;
        private final Counter nextPersonId;
        private final Counter nextCityId;
        private final Counter nextHobbyId;
        private final Set<PersonHobbyKey> inPackageHobbyFacts;
        private final List<MigrationRejection> rejections = new ArrayList<>();
        private final List<PendingHobby> pendingHobbies = new ArrayList<>();
        private final List<PendingProfile> pendingProfiles = new ArrayList<>();

        private Context(Map<String, Integer> personIdByLowerEmail, Map<CityKey, City> citiesByKey,
                int nextPersonId, int nextCityId, int nextHobbyId, Set<PersonHobbyKey> existingHobbyFacts) {
            this.personIdByLowerEmail = personIdByLowerEmail;
            this.citiesByKey = citiesByKey;
            this.nextPersonId = new Counter(nextPersonId);
            this.nextCityId = new Counter(nextCityId);
            this.nextHobbyId = new Counter(nextHobbyId);
            this.inPackageHobbyFacts = existingHobbyFacts;
        }
    }

    /**
     * Reads the file leniently as UTF-8 (malformed byte sequences become the U+FFFD replacement
     * character rather than aborting the read) and then parses the resulting text with a parser
     * hardened against external entities and DTDs.
     */
    private Document parseSecurely(Path xmlPath) {
        try {
            String xmlContent = new String(Files.readAllBytes(xmlPath), StandardCharsets.UTF_8);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read transfer package file: " + xmlPath, exception);
        } catch (SAXException | ParserConfigurationException exception) {
            throw new IllegalStateException("Could not parse transfer package file: " + xmlPath, exception);
        }
    }
}
