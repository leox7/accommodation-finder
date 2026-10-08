package com.Leon.accommodation_finder.util;

public class MaskingUtil {

    // 12345678 becomes ****5678. Null or 4 characters or fewer becomes ****
    public static String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.length() <= 4) {
            return "****";
        }
        return "****" + nationalId.substring(nationalId.length() - 4);
    }

    // 254712345678 becomes 2547****678. Null or 7 characters or fewer becomes ****
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 7) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
