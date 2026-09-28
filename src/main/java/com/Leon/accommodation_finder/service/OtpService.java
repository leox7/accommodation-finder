package com.Leon.accommodation_finder.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final long OTP_VALID_MILLIS = 5 * 60 * 1000; // 5 minutes
    private static final SecureRandom random = new SecureRandom();

    // Keyed by email, holds the current code and when it stops being valid.
    // ConcurrentHashMap because more than one login request could touch this
    // map at the same time, a plain HashMap is not safe for that.
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        long expiresAt = System.currentTimeMillis() + OTP_VALID_MILLIS;
        otpStore.put(email, new OtpEntry(otp, expiresAt));
        return otp;
    }

    public boolean isValid(String email, String otp) {
        OtpEntry entry = otpStore.get(email);

        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            otpStore.remove(email);
            return false;
        }
        return entry.otp.equals(otp);
    }

    // Called once an OTP has been used successfully, so it cannot be reused.
    public void clearOtp(String email) {
        otpStore.remove(email);
    }

    private static class OtpEntry {
        private final String otp;
        private final long expiresAt;

        private OtpEntry(String otp, long expiresAt) {
            this.otp = otp;
            this.expiresAt = expiresAt;
        }
    }
}