# Befundnotiz – LetsMeet-Datenmigration

**Zielschema (relationales Modell / ERD):**

```mermaid
erDiagram
    CITY {
        varchar zip_code PK
        varchar city_name "NOT NULL"
    }

    GENDER {
        int gender_id PK
        varchar label "NOT NULL; m | w | nb"
    }

    PERSON {
        int person_id PK
        varchar last_name "NOT NULL"
        varchar first_name "NOT NULL"
        varchar street
        varchar zip_code FK
        varchar phone_number
        varchar email "UNIQUE, NOT NULL"
        int gender_id FK
        date birth_date
    }

    HOBBY {
        int hobby_id PK
        int user_id FK
        varchar description "free text"
        smallint priority "0-100"
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

---

### 2026-08-19
- **Zielschema in die 3. Normalform gebracht.** Wiederholgruppen und Mehrfach-Zielgeschlechter
  aus einzelnen Spalten in eigene Relationen ausgelagert; Verweise über Fremdschlüssel.
  Grundlage für Modell und Import ist damit `databaseSchema.md`.
- **Pfad gefixt.** Verbindungs-/Zielpfad korrigiert, sodass der Import gegen die richtige
  Datenbank `lf8_lets_meet_db` läuft.
- **Nächster Schritt festgelegt:** Der `DatabaseMigrator` wird anhand dieses Schemas erstellt
  (DDL + Import), nicht frei improvisiert.
