package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AuditLogResponseDto {

    private Long id;
    private LocalDateTime timestamp;
    private String actorEmail;
    private String actorRole;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
}
