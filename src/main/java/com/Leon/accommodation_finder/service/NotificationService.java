package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.NotificationResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.exception.NotificationNotFoundException;
import com.Leon.accommodation_finder.exception.NotificationOwnershipException;
import com.Leon.accommodation_finder.model.Notification;
import com.Leon.accommodation_finder.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // landlordEmail is the currently logged in landlord, taken from their JWT
    public PagedResponse<NotificationResponseDto> getMyNotifications(String landlordEmail, int page, int size) {
        try {
            Page<Notification> notifications =
                    notificationRepository.findMyNotifications(landlordEmail, buildPageable(page, size));
            return PagedResponse.from(notifications.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching notifications for the logged in landlord: {}", e.getMessage());
            throw e;
        }
    }

    public NotificationResponseDto markAsRead(Long id, String landlordEmail) {
        try {
            Notification notification = notificationRepository.findById(id)
                    .orElseThrow(() -> new NotificationNotFoundException(id));

            if (!notification.getLandlord().getEmail().equalsIgnoreCase(landlordEmail)) {
                throw new NotificationOwnershipException();
            }

            notification.setRead(true);
            return toResponse(notificationRepository.save(notification));
        } catch (Exception e) {
            logger.error("Error while marking notification with id {} as read: {}", id, e.getMessage());
            throw e;
        }
    }

    // page is at least 0, size is between 1 and 50, newest notifications first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    private NotificationResponseDto toResponse(Notification notification) {
        NotificationResponseDto response = new NotificationResponseDto();
        response.setId(notification.getId());
        response.setMessage(notification.getMessage());
        response.setType(notification.getType());
        response.setRead(notification.isRead());
        response.setDateCreated(notification.getDateCreated());
        return response;
    }
}
