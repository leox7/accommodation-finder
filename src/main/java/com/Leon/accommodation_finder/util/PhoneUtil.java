package com.Leon.accommodation_finder.util;

import com.Leon.accommodation_finder.exception.InvalidPhoneNumberException;

public class PhoneUtil {

    // Turns the ways a student may type a Safaricom number into one form, 254XXXXXXXXX:
    // 0712345678, 0112345678, +254712345678, 254712345678 and "0712 345 678" all work.
    // Anything that does not end up as 2547XXXXXXXX or 2541XXXXXXXX gives a 400.
    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidPhoneNumberException();
        }

        String phone = raw.trim().replace(" ", "").replace("-", "");
        if (phone.startsWith("+")) {
            phone = phone.substring(1);
        }

        // local format: 0 followed by 9 digits, the 0 is replaced by Kenya's country code
        if (phone.matches("^0\\d{9}$")) {
            phone = "254" + phone.substring(1);
        }

        if (!phone.matches("^254[17]\\d{8}$")) {
            throw new InvalidPhoneNumberException();
        }
        return phone;
    }
}
