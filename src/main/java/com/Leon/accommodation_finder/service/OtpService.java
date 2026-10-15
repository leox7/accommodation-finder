package com.Leon.accommodation_finder.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final String KEY_PREFIX = "otp:";
    private static final String COOLDOWN_PREFIX = "otp-cooldown:";
    private static final SecureRandom random = new SecureRandom();

    // Redis holds one key per email, for example otp:jane@students.kcau.ac.ke -> 123456.
    // Because Redis lives outside the app, the codes survive an app restart.
    private final StringRedisTemplate redisTemplate;

    @Value("${otp.expiry-minutes}")
    private long otpExpiryMinutes;

    @Value("${otp.resend-cooldown-seconds}")
    private long resendCooldownSeconds;

    @Autowired
    public OtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        // the expiry (TTL) makes Redis delete the key by itself when time runs out,
        // and saving again for the same email replaces any older code
        redisTemplate.opsForValue().set(KEY_PREFIX + email, otp, Duration.ofMinutes(otpExpiryMinutes));
        // a fresh code also starts the resend cooldown, so a new one cannot be requested straight away
        redisTemplate.opsForValue().set(COOLDOWN_PREFIX + email, "1", Duration.ofSeconds(resendCooldownSeconds));
        return otp;
    }

    // true if a login code was sent to this email and has not expired or been used yet
    public boolean hasPendingOtp(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + email));
    }

    // Starts the resend cooldown for this email. Returns false if one is already running.
    // setIfAbsent is a single step in Redis, so two requests at the same moment cannot both pass.
    public boolean startResendCooldown(String email) {
        Boolean started = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + email, "1", Duration.ofSeconds(resendCooldownSeconds));
        return Boolean.TRUE.equals(started);
    }

    // seconds until the cooldown ends, at least 1
    public long resendCooldownSecondsLeft(String email) {
        Long seconds = redisTemplate.getExpire(COOLDOWN_PREFIX + email, TimeUnit.SECONDS);
        return (seconds == null || seconds < 1) ? 1 : seconds;
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
