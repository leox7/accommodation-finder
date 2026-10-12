package com.Leon.accommodation_finder.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpService {

    private static final String KEY_PREFIX = "otp:";
    private static final SecureRandom random = new SecureRandom();

    // Redis holds one key per email, for example otp:jane@students.kcau.ac.ke -> 123456.
    // Because Redis lives outside the app, the codes survive an app restart.
    private final StringRedisTemplate redisTemplate;

    @Value("${otp.expiry-minutes}")
    private long otpExpiryMinutes;

    @Autowired
    public OtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        // the expiry (TTL) makes Redis delete the key by itself when time runs out,
        // and saving again for the same email replaces any older code
        redisTemplate.opsForValue().set(KEY_PREFIX + email, otp, Duration.ofMinutes(otpExpiryMinutes));
        return otp;
    }

    public boolean isValid(String email, String otp) {
        // null means there was never a code, or it already expired or was used
        String storedOtp = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        return storedOtp != null && storedOtp.equals(otp);
    }

    // Called once an OTP has been used successfully, so it cannot be reused.
    public void clearOtp(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }
}
