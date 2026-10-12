package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.ReportResponseDto;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.ReportGroupBy;
import com.Leon.accommodation_finder.model.ReportSection;
import com.Leon.accommodation_finder.model.ReportSort;
import com.Leon.accommodation_finder.service.ReportService;
import com.Leon.accommodation_finder.service.ReportViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Report data for landlords, about their own listings only. The admin report is in AdminController.
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportViewService reportViewService;

    @Autowired
    public ReportController(ReportService reportService, ReportViewService reportViewService) {
        this.reportService = reportService;
        this.reportViewService = reportViewService;
    }

    // e.g. /api/reports/landlord?from=2026-01-01&to=2026-10-31&paymentStatus=PAID&groupBy=MONTH
    // every parameter is optional, a wrong date or enum value gives 400
    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping("/landlord")
    public ReportResponseDto getLandlordReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingStatus bookingStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "STATUS") ReportGroupBy groupBy,
            @RequestParam(defaultValue = "GROUP_ASC") ReportSort sort) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return reportService.getLandlordReport(email, from, to, bookingStatus, paymentStatus, groupBy, sort);
    }

    // The same report as a printable HTML page (Ctrl+P in the browser gives a PDF). Same parameters, plus
    // sections, e.g. ?sections=BOOKINGS,INCOME. Without sections all four are shown.
    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping(value = "/landlord/view", produces = "text/html;charset=UTF-8")
    public String getLandlordReportView(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingStatus bookingStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "STATUS") ReportGroupBy groupBy,
            @RequestParam(defaultValue = "GROUP_ASC") ReportSort sort,
            @RequestParam(defaultValue = "LISTINGS,BOOKINGS,PAYMENTS,INCOME") List<ReportSection> sections) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return reportViewService.renderLandlordReport(email, from, to, bookingStatus, paymentStatus,
                groupBy, sort, sections);
    }
}
