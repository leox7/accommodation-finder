package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.AdminOverviewDto;
import com.Leon.accommodation_finder.dto.AdminRegisterDto;
import com.Leon.accommodation_finder.dto.AuditLogResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.exception.EmailAlreadyExistsException;
import com.Leon.accommodation_finder.exception.UserDeactivationException;
import com.Leon.accommodation_finder.exception.UserNotFoundException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.Admin;
import com.Leon.accommodation_finder.model.AuditLog;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.Role;
import com.Leon.accommodation_finder.model.User;
import com.Leon.accommodation_finder.repository.AdminRepository;
import com.Leon.accommodation_finder.repository.AuditLogRepository;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import com.Leon.accommodation_finder.repository.PaymentRepository;
import com.Leon.accommodation_finder.repository.UserRepository;
import com.Leon.accommodation_finder.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;
    private final ListingRepository listingRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Autowired
    public AdminService(UserRepository userRepository, AuditLogRepository auditLogRepository,
                        AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                        ApplicationEventPublisher eventPublisher, RefreshTokenService refreshTokenService,
                        ListingRepository listingRepository, BookingRepository bookingRepository,
                        PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.refreshTokenService = refreshTokenService;
        this.listingRepository = listingRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    // Counts for the admin dashboard, kept in Redis for 60 seconds.
    // Cache miss: the counts are taken from the database (the log line below appears) and stored.
    // Cache hit: Spring returns the stored copy and this method does not run at all (no log line).
    // Only works when called from another class (the controller): Spring adds the caching around
    // the bean, so a call from inside this class would skip the cache.
    @Cacheable("adminOverview")
    public AdminOverviewDto getOverview() {
        try {
            logger.info("Overview counts calculated from the database");

            AdminOverviewDto overview = new AdminOverviewDto();
            overview.setTotalUsers(userRepository.count());
            overview.setTotalListings(listingRepository.count());
            overview.setTotalBookings(bookingRepository.count());
            overview.setTotalPayments(paymentRepository.count());
            overview.setGeneratedAt(LocalDateTime.now());
            return overview;
        } catch (Exception e) {
            logger.error("Error while calculating the admin overview: {}", e.getMessage());
            throw e;
        }
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
                dto.setCreatedAt(user.getDateCreated());
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

    // Only an admin can create another admin. adminEmail is the logged in admin, taken from their JWT.
    public UserResponseDto createAdmin(AdminRegisterDto dto, String adminEmail) {
        try {
            String email = dto.getEmail().trim().toLowerCase();

            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException(email);
            }

            Admin admin = new Admin();
            admin.setFirstName(dto.getFirstName());
            admin.setLastName(dto.getLastName());
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(dto.getPassword()));
            // role is set by the system, never taken from the request
            admin.setRole(Role.ADMIN);
            Admin saved = adminRepository.save(admin);

            // never put the password in an event
            eventPublisher.publishEvent(new DomainEvent("admin.created", adminEmail, "ADMIN",
                    "User", saved.getId(), "Admin account created"));

            UserResponseDto response = toUserResponse(saved);
            response.setMessage("Admin account created successfully");
            return response;
        } catch (Exception e) {
            logger.error("Error while creating admin with email '{}': {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    // Stops a user from logging in again and ends all their sessions.
    // Transactional: enabled=false and the revoked refresh tokens are saved together, or not at all,
    // and the user.deactivated event is only sent once both are committed.
    // Access tokens already issued keep working until they expire (10 minutes at most).
    @Transactional
    public UserResponseDto deactivateUser(Long id, String adminEmail) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));

            if (user.getEmail().equalsIgnoreCase(adminEmail)) {
                throw new UserDeactivationException("You cannot deactivate yourself");
            }
            if (!user.isEnabled()) {
                throw new UserDeactivationException("This user is already deactivated");
            }

            user.setEnabled(false);
            userRepository.save(user);
            refreshTokenService.revokeAllForUser(user);

            eventPublisher.publishEvent(new DomainEvent("user.deactivated", adminEmail, "ADMIN",
                    "User", user.getId(), user.getRole() + " account deactivated, refresh tokens revoked"));

            UserResponseDto response = toUserResponse(user);
            response.setMessage("User deactivated");
            return response;
        } catch (Exception e) {
            logger.error("Error while deactivating user with id {}: {}", id, e.getMessage());
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

    // no national ID and no password, only the basic account details
    private UserResponseDto toUserResponse(User user) {
        UserResponseDto response = new UserResponseDto();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setCreatedAt(user.getDateCreated());
        return response;
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
