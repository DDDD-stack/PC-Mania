package al.pcmania.service.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiStripperTest {

    @Test
    void phoneNumbersAndEmailsAreRemoved() {
        PiiStripper.Result r = PiiStripper.strip("Më telefono në 069 123 4567 ose +355 68 834 3551, emaili arben@example.com");
        assertTrue(r.stripped());
        assertFalse(r.text().contains("069"), r.text());
        assertFalse(r.text().contains("3551"), r.text());
        assertFalse(r.text().contains("@"), r.text());
        assertEquals("Më telefono në [numër telefoni hequr] ose [numër telefoni hequr], emaili [email hequr]", r.text());
        assertTrue(PiiStripper.strip("0691234567").stripped());
        assertTrue(PiiStripper.strip("069-123-4567").stripped());
    }

    @Test
    void pricesAndModelNumbersSurvive() {
        for (String s : new String[]{"Kam 40000 lekë dhe PSU 650W", "buxheti 150 000 lekë", "RTX 3060 Ti 12GB për 38.000 Lekë",
                "1.250.000 lekë", "i7 12700K me 32 GB RAM", "GTX 1660 Super 2019"}) {
            PiiStripper.Result r = PiiStripper.strip(s);
            assertFalse(r.stripped(), s);
            assertEquals(s, r.text());
        }
        assertEquals("", PiiStripper.strip(null).text());
    }
}
