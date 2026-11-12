package de.trion.training.keycloak.hibp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-Tests: reine Logik ohne Keycloak-Server, laufen in Millisekunden ("mvn test").
 */
@Disabled("Übung Password Policy: zum Start dieser Übung diese Zeile entfernen")
class HibpPasswordPolicyProviderTest {

    @Test
    void sha1IsUpperCaseHex() {
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", HibpPasswordPolicyProvider.sha1Hex("password"));
    }

    @Test
    void findsCountForSuffix() {
        String body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:9659365\r\n";

        assertEquals(9659365, HibpPasswordPolicyProvider.countInResponse(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"));
    }

    @Test
    void comparesSuffixCaseInsensitive() {
        assertEquals(3, HibpPasswordPolicyProvider.countInResponse("ABCDEF:3", "abcdef"));
    }

    @Test
    void returnsZeroWhenSuffixIsMissing() {
        assertEquals(0, HibpPasswordPolicyProvider.countInResponse("0018A45C4D1DEF81644B54AB7F969B88D65:1", "ABCDEF"));
    }

    @Test
    void acceptsUnixLineEndings() {
        assertEquals(2, HibpPasswordPolicyProvider.countInResponse("AAA:1\nBBB:2\n", "BBB"));
    }
}
