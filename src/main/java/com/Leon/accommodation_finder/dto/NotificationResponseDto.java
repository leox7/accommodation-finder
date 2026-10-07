package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.NotificationType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class NotificationResponseDto {

    private Long id;
    private String message;
    private NotificationType type;
    private boolean read;
    private LocalDateTime dateCreated;
}
