package com.Leon.accommodation_finder.util;

public class MaskingUtil {

    // 12345678 becomes ****5678. Null or 4 characters or fewer becomes ****
    public static String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.length() <= 4) {
            return "****";
        }
        return "****" + nationalId.substring(nationalId.length() - 4);
    }
}
