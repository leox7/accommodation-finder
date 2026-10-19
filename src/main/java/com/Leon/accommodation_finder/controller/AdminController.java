package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.AdminBookingResponseDto;
import com.Leon.accommodation_finder.dto.AdminOverviewDto;
import com.Leon.accommodation_finder.dto.AdminRegisterDto;
import com.Leon.accommodation_finder.dto.AdminStatsDto;
import com.Leon.accommodation_finder.dto.AuditLogResponseDto;
import com.Leon.accommodation_finder.dto.InquiryResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.PaymentResponseDto;
import com.Leon.accommodation_finder.dto.ReportResponseDto;
import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.ReportGroupBy;
import com.Leon.accommodation_finder.model.ReportSection;
import com.Leon.accommodation_finder.model.ReportSort;
import com.Leon.accommodation_finder.service.AdminService;
import com.Leon.accommodation_finder.service.BookingService;
import com.Leon.accommodation_finder.service.InquiryService;
import com.Leon.accommodation_finder.service.PaymentService;
import com.Leon.accommodation_finder.service.ReportService;
import com.Leon.accommodation_finder.service.ReportViewService;
import com.Leon.accommodation_finder.service.StatsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ReportService reportService;
    private final ReportViewService reportViewService;
    private final StatsService statsService;
    private final InquiryService inquiryService;

    @Autowired
    public AdminController(AdminService adminService, BookingService bookingService,
                           PaymentService paymentService, ReportService reportService,
                           ReportViewService reportViewService, StatsService statsService,
                           InquiryService inquiryService) {
        this.adminService = adminService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.reportService = reportService;
        this.reportViewService = reportViewService;
        this.statsService = statsService;
        this.inquiryService = inquiryService;
    }

    // counts of users, listings, bookings and payments, up to 60 seconds old (cached in Redis)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview")
    public AdminOverviewDto getOverview() {
        return adminService.getOverview();
    }

    // dashboard numbers: users, listings, pending bookings and money received this month and last month
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public AdminStatsDto getStats() {
        return statsService.getAdminStats();
    }

    // messages from the public contact form, newest first
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inquiries")
    public PagedResponse<InquiryResponseDto> getInquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return inquiryService.getInquiries(page, size);
    }

    // system wide report data, the same options as /api/reports/landlord
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/reports")
    public ReportResponseDto getAdminReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingStatus bookingStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "STATUS") ReportGroupBy groupBy,
            @RequestParam(defaultValue = "GROUP_ASC") ReportSort sort) {
        return reportService.getAdminReport(from, to, bookingStatus, paymentStatus, groupBy, sort);
    }

    // the admin report as a printable HTML page, the same parameters plus sections
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/reports/view", produces = "text/html;charset=UTF-8")
    public String getAdminReportView(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingStatus bookingStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "STATUS") ReportGroupBy groupBy,
            @RequestParam(defaultValue = "GROUP_ASC") ReportSort sort,
            @RequestParam(defaultValue = "LISTINGS,BOOKINGS,PAYMENTS,INCOME") List<ReportSection> sections) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return reportViewService.renderAdminReport(email, from, to, bookingStatus, paymentStatus,
                groupBy, sort, sections);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public List<UserResponseDto> getAllUsers() {
        return adminService.getAllUsers();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admins")
    public ResponseEntity<UserResponseDto> createAdmin(@Valid @RequestBody AdminRegisterDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserResponseDto created = adminService.createAdmin(dto, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    // the user can no longer log in, and all their refresh tokens are revoked
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}/deactivate")
    public UserResponseDto deactivateUser(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return adminService.deactivateUser(id, email);
    }

    // e.g. /api/admin/bookings?status=CONFIRMED, without status every booking is listed
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/bookings")
    public PagedResponse<AdminBookingResponseDto> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return bookingService.getAllBookings(status, page, size);
    }

    // e.g. /api/admin/payments?status=PAID, phone numbers stay masked
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payments")
    public PagedResponse<PaymentResponseDto> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.getAllPayments(status, page, size);
    }

    // e.g. /api/admin/audit-logs?action=booking.created&from=2026-10-01&to=2026-10-31
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs")
    public PagedResponse<AuditLogResponseDto> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return adminService.getAuditLogs(action, from, to, page, size);
    }
}
