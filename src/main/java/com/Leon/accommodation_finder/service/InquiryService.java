package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.InquiryRequestDto;
import com.Leon.accommodation_finder.dto.InquiryResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.exception.TooManyRequestsException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.Inquiry;
import com.Leon.accommodation_finder.repository.InquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class InquiryService {

    private static final Logger logger = LoggerFactory.getLogger(InquiryService.class);

    private static final int MAX_PAGE_SIZE = 50;
    private static final String LIMIT_KEY_PREFIX = "inquiry-limit:";

    private final InquiryRepository inquiryRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${inquiry.max-per-hour}")
    private int maxPerHour;

    @Autowired
    public InquiryService(InquiryRepository inquiryRepository, StringRedisTemplate redisTemplate,
                          ApplicationEventPublisher eventPublisher) {
        this.inquiryRepository = inquiryRepository;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    // Public, no login. ipAddress is only used to count how many forms one address sends per hour.
    // The support email is sent by the notification consumer after the inquiry is saved,
    // so a mail problem can never make the visitor's form fail.
    public InquiryResponseDto createInquiry(InquiryRequestDto dto, String ipAddress) {
        try {
            checkRateLimit(ipAddress);

            Inquiry inquiry = new Inquiry();
            inquiry.setFullName(dto.getFullName().trim());
            inquiry.setEmail(dto.getEmail().trim().toLowerCase());
            inquiry.setSubject(dto.getSubject());
            inquiry.setMessage(dto.getMessage().trim());
            inquiry.setDateCreated(LocalDateTime.now());
            Inquiry saved = inquiryRepository.save(inquiry);

            // the visitor is not logged in, and the event carries no message text or email
            eventPublisher.publishEvent(new DomainEvent("inquiry.created", "anonymous", "PUBLIC",
                    "Inquiry", saved.getId(), "Contact form: " + saved.getSubject().getLabel()));
            return toResponse(saved);
        } catch (Exception e) {
            logger.error("Error while saving an inquiry: {}", e.getMessage());
            throw e;
        }
    }

    // newest first
    public PagedResponse<InquiryResponseDto> getInquiries(int page, int size) {
        try {
            Page<Inquiry> inquiries = inquiryRepository.findAll(buildPageable(page, size));
            return PagedResponse.from(inquiries.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching inquiries: {}", e.getMessage());
            throw e;
        }
    }

    // Redis counts the forms sent from one IP address. The counter key lives for one hour from the first form,
    // after that Redis deletes it and the address starts again from zero.
    private void checkRateLimit(String ipAddress) {
        String key = LIMIT_KEY_PREFIX + ipAddress;
        Long count = redisTemplate.opsForValue().increment(key);

        // a key without an expiry would block this address for ever, so make sure it always has one
        Long secondsLeft = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (secondsLeft == null || secondsLeft < 0) {
            redisTemplate.expire(key, Duration.ofHours(1));
            secondsLeft = 3600L;
        }

        if (count != null && count > maxPerHour) {
            throw new TooManyRequestsException(
                    "You have sent too many messages. Please try again later", Math.max(secondsLeft, 1));
        }
    }

    // page is at least 0, size is between 1 and 50, newest first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    private InquiryResponseDto toResponse(Inquiry inquiry) {
        InquiryResponseDto response = new InquiryResponseDto();
        response.setId(inquiry.getId());
        response.setFullName(inquiry.getFullName());
        response.setEmail(inquiry.getEmail());
        response.setSubject(inquiry.getSubject());
        response.setMessage(inquiry.getMessage());
        response.setCreatedAt(inquiry.getDateCreated());
        return response;
    }
}
