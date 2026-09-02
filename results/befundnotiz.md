# Befundnotiz – LetsMeet-Datenmigration

---

### 13.8.2026 

- Wir habens uns für Java für die Programmiersprache des Projektes entschieden.
- Für Bibliotheken haben wir uns für JDBC mit dem Postgres Driver für die Datenbank verbindung entschieden und für das Auslesen von Excel daten verwenden wir Apache POI.
- Wir haben das basis Auslesen der Excel daten und die Vorlage für die Datenbank verbindung implementiert.

### 19.8.2026

- Wir haben das Datenschema für die Datenbank-Tabellen entwickelt:

```mermaid
erDiagram
    CITY {
        int city_id PK
        varchar zip_code "NOT NULL"
        varchar city_name "UNIQUE (zip_code, city_name), NOT NULL"
    }

    GENDER {
        int gender_id PK
        varchar label "NOT NULL"
    }

    PERSON {
        int person_id PK
        varchar last_name "NOT NULL"
        varchar first_name "NOT NULL"
        varchar street
        varchar street_number
        int city_id FK
        varchar phone_number
        varchar email "UNIQUE, NOT NULL"
        int gender_id FK
        date birth_date
    }

    HOBBY {
        int hobby_id PK
        int user_id FK
        text description
        smallint priority "CHECK -100..100"
    }

    PERSON_INTEREST {
        int person_id PK,FK
        int gender_id PK,FK
    }

    CITY      ||--o{ PERSON      : "located in"
    GENDER    ||--o{ PERSON      : "has"
    PERSON    ||--o{ HOBBY         : "writes"
    PERSON    ||--o{ PERSON_INTEREST : "is interested in"
    GENDER    ||--o{ PERSON_INTEREST : "is target of"
```

Dieses Schema bringt die Daten bis in die dritte Normalform.

- Wir haben uns dafür entschieden, Datenmodelle (Java Records) für die verschiedenen Tabellen zu erstellen, damit die migration im code Strukturierter vorgeht
- Wir haben das Einlesen der Modelle und die Migration in die Datenbank implementiert.
- Wir haben das Prüfungsskript ausgeführt und erfolgreich bestanden. Wir hatten nur den warnhinweis von ``"Im Bestand: 15 Postleitzahlen mit führender Null 
  (Quelle: 15). 58 Personen mit vierstelliger Postleitzahl (Quelle: 58)."`` bekommen.

### 27.8.2026

- Wir führen Excel und MongoDB über die E-Mail-Adresse ohne Beachtung der
  Groß-/Kleinschreibung zusammen. In den Views bleibt trotzdem die Schreibweise aus Excel erhalten,
  weil der V2-Datenvertrag dies ausdrücklich verlangt.
- Bei widersprüchlichen Profilfeldern gilt die eingeholte Kundinnenentscheidung `MONGO_WINS`:
  MongoDB gewinnt bei Vorname, Nachname und Telefonnummer, Excel ergänzt nur fehlende Werte. Gegen
  einen allgemeinen Vorrang von Excel haben wir uns entschieden, weil die MongoDB-Daten die
  nachgelieferte Quelle für diese Profilangaben sind.

### 28.8.2026

- Das Zielmodell speichert genau ein Profilbild direkt in `PERSON.profile_image`. Weitere Fotos
  liegen in `PHOTO` und enthalten entweder Binärdaten oder eine URL, niemals beides. Die MongoDB-
  Lieferung besitzt kein Foto-, Bild- oder Avatar-Feld; deshalb erfinden wir keine Platzhalter und
  importieren aktuell keine Fotos.
- Freundschaften speichern wir als symmetrische Beziehung in `PERSON_FRIEND` und jedes Paar nur
  einmal in kanonischer Reihenfolge (`person_id_low < person_id_high`). Eine gerichtete Speicherung
  wie bei Likes haben wir verworfen, weil eine Freundschaft beiden Personen zugeordnet ist. Alle
  1.576 gelieferten `friends`-Arrays sind leer; ohne belegtes Elementformat importieren wir daraus
  keine Beziehungen.
- Namen, Kontaktdaten, Interessen, Nachrichten und Bilder sind personenbezogen. Interessen können
  zudem Rückschlüsse auf besonders geschützte Angaben zulassen. Deshalb verarbeitet der Import die
  Daten nur lokal und protokolliert bei Konflikten keine Feldwerte. Die konkrete

```mermaid
erDiagram
    CITY {
        int city_id PK
        varchar zip_code "NOT NULL"
        varchar city_name "NOT NULL, UNIQUE (zip_code, city_name)"
    }

    GENDER {
        int gender_id PK
        varchar label "NOT NULL"
    }

    PERSON {
        int person_id PK
        varchar last_name "NOT NULL"
        varchar first_name "NOT NULL"
        varchar street
        varchar street_number
        int city_id FK
        varchar phone_number
        varchar email "UNIQUE, NOT NULL, case-insensitive index"
        int gender_id FK
        date birth_date
        bytea profile_image
    }

    PHOTO {
        int photo_id PK "GENERATED ALWAYS AS IDENTITY"
        int person_id FK "NOT NULL"
        bytea image_data "exactly one of image_data/image_url"
        text image_url "exactly one of image_data/image_url"
    }

    PERSON_FRIEND {
        int person_id_low PK, FK "NOT NULL, CHECK low < high"
        int person_id_high PK, FK "NOT NULL, CHECK low < high"
    }

    HOBBY {
        int hobby_id PK
        int user_id FK
        text description
        smallint priority "CHECK -100..100"
    }

    PERSON_INTEREST {
        int person_id PK, FK
        int gender_id PK, FK
    }

    PERSON_INTEREST_TEXT {
        int person_id PK, FK
        text interest_code PK
    }

    PERSON_LIKE {
        int like_id PK
        int liker_person_id FK "NOT NULL"
        int liked_person_id FK "NOT NULL"
        varchar status "NOT NULL"
        timestamp liked_at "NOT NULL"
    }

    PERSON_MESSAGE {
        int message_id PK
        int sender_person_id FK "NOT NULL"
        int receiver_person_id FK "NOT NULL"
        int conversation_id "NOT NULL"
        text body "NOT NULL"
        timestamp sent_at "NOT NULL"
    }

    CITY ||--o{ PERSON : "located in"
    GENDER ||--o{ PERSON : "has"
    PERSON ||--o{ HOBBY : "has"
    PERSON ||--o{ PHOTO : "has"
    PERSON ||--o{ PERSON_FRIEND : "is low side of"
    PERSON ||--o{ PERSON_FRIEND : "is high side of"
    PERSON ||--o{ PERSON_INTEREST : "is interested in"
    GENDER ||--o{ PERSON_INTEREST : "is target of"
    PERSON ||--o{ PERSON_INTEREST_TEXT : "has raw interest"
    PERSON ||--o{ PERSON_LIKE : "likes"
    PERSON ||--o{ PERSON_LIKE : "is liked"
    PERSON ||--o{ PERSON_MESSAGE : "sends"
    PERSON ||--o{ PERSON_MESSAGE : "receives"
```
