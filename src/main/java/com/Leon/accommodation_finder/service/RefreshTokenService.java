package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.exception.InvalidRefreshTokenException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.RefreshToken;
import com.Leon.accommodation_finder.model.User;
import com.Leon.accommodation_finder.repository.RefreshTokenRepository;
import com.Leon.accommodation_finder.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtUtil jwtUtil,
                               ApplicationEventPublisher eventPublisher) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
        this.eventPublisher = eventPublisher;
    }

    // Makes a new refresh token for the user and stores its hash. The raw token
    // is returned to the caller once and never saved anywhere.
    public String createRefreshToken(User user) {
        try {
            String token = jwtUtil.generateRefreshToken(user);

            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setUser(user);
            refreshToken.setTokenHash(hash(token));
            refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000));
            refreshToken.setRevoked(false);
            refreshToken.setCreatedAt(LocalDateTime.now());
            refreshTokenRepository.save(refreshToken);

            return token;
        } catch (Exception e) {
            logger.error("Error while creating a refresh token for user id {}: {}", user.getId(), e.getMessage());
            throw e;
        }
    }

    // Checks a refresh token, revokes it so it can only be used once, and returns its owner.
    // noRollbackFor: when a revoked token is reused we revoke all the user's tokens and then
    // throw. Without it, the throw would undo the "revoke all" before it reached the database.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public User useRefreshToken(String token) {
        try {
            if (!jwtUtil.isValid(token) || !jwtUtil.isRefreshToken(token)) {
                throw new InvalidRefreshTokenException();
            }

            RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(token))
                    .orElseThrow(InvalidRefreshTokenException::new);

            if (stored.isRevoked()) {
                // an old token came back, it may have been stolen, so end every session of this user
                logger.warn("Revoked refresh token reused for user id {}, revoking all their refresh tokens",
                        stored.getUser().getId());
                revokeAllForUser(stored.getUser());
                throw new InvalidRefreshTokenException();
            }
            if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new InvalidRefreshTokenException();
            }

            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            return stored.getUser();
        } catch (Exception e) {
            logger.error("Error while using a refresh token: {}", e.getMessage());
            throw e;
        }
    }

    // Unknown, expired or already revoked tokens are simply ignored, logout always succeeds.
    // Only a logout with a token we know is audited, a garbage token has no user to record.
    @Transactional
    public void logout(String token) {
        try {
            refreshTokenRepository.findByTokenHash(hash(token)).ifPresent(stored -> {
                stored.setRevoked(true);
                refreshTokenRepository.save(stored);
                User user = stored.getUser();
                eventPublisher.publishEvent(new DomainEvent("auth.logout", user.getEmail(), user.getRole().name(),
                        "User", user.getId(), "Refresh token revoked"));
            });
        } catch (Exception e) {
            logger.error("Error while logging out: {}", e.getMessage());
            throw e;
        }
    }

    // Logs the user out everywhere. Also used later when an admin deactivates a user.
    @Transactional
    public void revokeAllForUser(User user) {
        try {
            refreshTokenRepository.revokeAllByUser(user);
        } catch (Exception e) {
            logger.error("Error while revoking refresh tokens for user id {}: {}", user.getId(), e.getMessage());
            throw e;
        }
    }

    // Every day at 03:00, removes rows whose tokens can no longer be used anyway.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        logger.info("Deleted {} expired refresh tokens", deleted);
    }

    // SHA-256 turns the token into a fixed 64 character fingerprint. The same token always
    // gives the same hash, so we can look it up, but the hash cannot be turned back into the token.
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // every Java installation includes SHA-256, so this should never happen
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
