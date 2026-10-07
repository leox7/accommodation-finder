package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.NotificationResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

// A landlord reads their own notifications and marks them read.
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping
    public PagedResponse<NotificationResponseDto> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return notificationService.getMyNotifications(email, page, size);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @PutMapping("/{id}/read")
    public NotificationResponseDto markAsRead(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return notificationService.markAsRead(id, email);
    }
}
