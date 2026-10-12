package com.Leon.accommodation_finder.util;

import com.Leon.accommodation_finder.exception.InvalidPhoneNumberException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneUtilTest {

    @Test
    void localSafaricomNumberStartingWith07() {
        assertEquals("254712345678", PhoneUtil.normalize("0712345678"));
    }

    @Test
    void localSafaricomNumberStartingWith01() {
        assertEquals("254112345678", PhoneUtil.normalize("0112345678"));
    }

    @Test
    void internationalFormatWithPlus() {
        assertEquals("254712345678", PhoneUtil.normalize("+254712345678"));
    }

    @Test
    void internationalFormatWithoutPlus() {
        assertEquals("254712345678", PhoneUtil.normalize("254712345678"));
    }

    @Test
    void spacesAreRemoved() {
        assertEquals("254712345678", PhoneUtil.normalize("0712 345 678"));
    }

    @Test
    void tooShortIsRejected() {
        assertThrows(InvalidPhoneNumberException.class, () -> PhoneUtil.normalize("12345"));
    }

    @Test
    void tooLongIsRejected() {
        assertThrows(InvalidPhoneNumberException.class, () -> PhoneUtil.normalize("07123456789"));
    }

    @Test
    void lettersAreRejected() {
        assertThrows(InvalidPhoneNumberException.class, () -> PhoneUtil.normalize("abcdefghij"));
    }

    @Test
    void numberStartingWith09IsRejected() {
        assertThrows(InvalidPhoneNumberException.class, () -> PhoneUtil.normalize("0912345678"));
    }
}
