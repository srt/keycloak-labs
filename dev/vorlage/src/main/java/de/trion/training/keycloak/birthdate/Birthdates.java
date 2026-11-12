package de.trion.training.keycloak.birthdate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Hilfsfunktionen rund um das Geburtsdatum - ohne Keycloak-Abhängigkeit, daher einfach per Unit-Test prüfbar. */
public final class Birthdates {

    /** User-Attribut mit dem Geburtsdatum im ISO-Format, z.B. 1990-04-01 */
    public static final String USER_ATTRIBUTE = "birthdate";

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("d.M.uuuu");

    private Birthdates() {
    }

    /**
     * Akzeptiert "1.4.1990", "01.04.1990" und "1990-04-01"; liefert ISO-Format oder null.
     * TODO 0 (Aufwärmen): BirthdatesTest ist rot - welche Eingabe wird falsch behandelt und warum?
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim();
        try {
            return (value.contains(".") ? LocalDate.parse(value, GERMAN_DATE) : LocalDate.parse(value)).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
