package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.AuditLogResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.model.AuditLog;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.User;
import com.Leon.accommodation_finder.repository.AuditLogRepository;
import com.Leon.accommodation_finder.repository.UserRepository;
import com.Leon.accommodation_finder.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public AdminService(UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<UserResponseDto> getAllUsers() {
        try {
            List<UserResponseDto> users = new ArrayList<>();

            for (User user : userRepository.findAll()) {
                UserResponseDto dto = new UserResponseDto();
                dto.setId(user.getId());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setEmail(user.getEmail());
                dto.setRole(user.getRole());
                if (user instanceof Landlord landlord) {
                    dto.setNationalIdNumber(MaskingUtil.maskNationalId(landlord.getNationalIdNumber()));
                }
                users.add(dto);
            }

            return users;
        } catch (Exception e) {
            logger.error("Error while fetching all users: {}", e.getMessage());
            throw e;
        }
    }

    // action, from and to are optional. "to" includes the whole of that day.
    public PagedResponse<AuditLogResponseDto> getAuditLogs(String action, LocalDate from, LocalDate to,
                                                           int page, int size) {
        try {
            // a blank action means "no action filter"
            if (action != null && action.isBlank()) {
                action = null;
            }
            LocalDateTime fromTime = (from == null) ? null : from.atStartOfDay();
            LocalDateTime toTime = (to == null) ? null : to.plusDays(1).atStartOfDay();

            Page<AuditLog> logs = auditLogRepository.search(action, fromTime, toTime, buildPageable(page, size));
            return PagedResponse.from(logs.map(this::toAuditLogResponse));
        } catch (Exception e) {
            logger.error("Error while fetching audit logs: {}", e.getMessage());
            throw e;
        }
    }

    // page is at least 0, size is between 1 and 50, newest rows first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    private AuditLogResponseDto toAuditLogResponse(AuditLog auditLog) {
        AuditLogResponseDto response = new AuditLogResponseDto();
        response.setId(auditLog.getId());
        response.setTimestamp(auditLog.getTimestamp());
        response.setActorEmail(auditLog.getActorEmail());
        response.setActorRole(auditLog.getActorRole());
        response.setAction(auditLog.getAction());
        response.setEntityType(auditLog.getEntityType());
        response.setEntityId(auditLog.getEntityId());
        response.setDetails(auditLog.getDetails());
        return response;
    }
}
