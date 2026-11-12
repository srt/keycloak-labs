package de.trion.training.keycloak.birthdate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BirthdatesTest {

    @ParameterizedTest
    @CsvSource({
            "1.4.1990,    1990-04-01",
            "01.04.1990,  1990-04-01",
            "1990-04-01,  1990-04-01",
            "' 1.4.1990 ', 1990-04-01"
    })
    void normalizesToIso(String input, String expected) {
        assertEquals(expected, Birthdates.normalize(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "31.02.1990", "1990/04/01", "April 1990", "1.4.90"})
    void rejectsInvalidInput(String input) {
        assertNull(Birthdates.normalize(input));
    }
}
