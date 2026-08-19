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
        smallint priority "CHECK 0-100"
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

