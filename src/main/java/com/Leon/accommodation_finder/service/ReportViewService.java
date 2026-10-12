package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.ReportResponseDto;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.ReportGroupBy;
import com.Leon.accommodation_finder.model.ReportSection;
import com.Leon.accommodation_finder.model.ReportSort;
import io.pebbletemplates.pebble.PebbleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Printable HTML pages of the reports. The numbers come from the SAME ReportService methods as the
// JSON endpoints, this class only fills them into templates/report.peb with Pebble.
// Each page counts as an export and is audited with a report.exported event.
@Service
public class ReportViewService {

    private static final Logger logger = LoggerFactory.getLogger(ReportViewService.class);

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter GENERATED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportService reportService;
    private final PebbleEngine pebbleEngine;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ReportViewService(ReportService reportService, PebbleEngine pebbleEngine,
                             ApplicationEventPublisher eventPublisher) {
        this.reportService = reportService;
        this.pebbleEngine = pebbleEngine;
        this.eventPublisher = eventPublisher;
    }

    // landlordEmail is the logged in landlord, so the page only ever shows their own numbers
    public String renderLandlordReport(String landlordEmail, LocalDate from, LocalDate to,
                                       BookingStatus bookingStatus, PaymentStatus paymentStatus,
                                       ReportGroupBy groupBy, ReportSort sort, List<ReportSection> sections) {
        ReportResponseDto report = reportService.getLandlordReport(landlordEmail, from, to,
                bookingStatus, paymentStatus, groupBy, sort);
        List<ReportSection> chosen = chosenSections(sections);
        String html = render("Landlord report", report, bookingStatus, paymentStatus, sort, chosen);
        publishReportExported(landlordEmail, "LANDLORD", "landlord report view", report, sort, chosen);
        return html;
    }

    public String renderAdminReport(String adminEmail, LocalDate from, LocalDate to,
                                    BookingStatus bookingStatus, PaymentStatus paymentStatus,
                                    ReportGroupBy groupBy, ReportSort sort, List<ReportSection> sections) {
        ReportResponseDto report = reportService.getAdminReport(from, to, bookingStatus, paymentStatus, groupBy, sort);
        List<ReportSection> chosen = chosenSections(sections);
        String html = render("Admin report", report, bookingStatus, paymentStatus, sort, chosen);
        publishReportExported(adminEmail, "ADMIN", "admin report view", report, sort, chosen);
        return html;
    }

    // the chosen sections in the normal order, each once, even if the URL repeats one
    private List<ReportSection> chosenSections(List<ReportSection> sections) {
        List<ReportSection> chosen = new ArrayList<>();
        for (ReportSection section : ReportSection.values()) {
            if (sections.contains(section)) {
                chosen.add(section);
            }
        }
        return chosen;
    }

    private String render(String title, ReportResponseDto report, BookingStatus bookingStatus,
                          PaymentStatus paymentStatus, ReportSort sort, List<ReportSection> sections) {
        try {
            // the values the template can use, for example {{ title }} or {{ report.income }}
            Map<String, Object> context = new HashMap<>();
            context.put("title", title);
            context.put("generatedAt", ZonedDateTime.now(NAIROBI).format(GENERATED_FORMAT));
            context.put("report", report);
            context.put("sort", sort);
            // null when not chosen, the template then shows "All"
            context.put("bookingStatus", bookingStatus);
            context.put("paymentStatus", paymentStatus);
            context.put("showListings", sections.contains(ReportSection.LISTINGS));
            context.put("showBookings", sections.contains(ReportSection.BOOKINGS));
            context.put("showPayments", sections.contains(ReportSection.PAYMENTS));
            context.put("showIncome", sections.contains(ReportSection.INCOME));

            // "report" is found as templates/report.peb. Locale.ENGLISH makes amounts look like 12,500.00
            // whatever language the server runs in.
            StringWriter writer = new StringWriter();
            pebbleEngine.getTemplate("report").evaluate(writer, context, Locale.ENGLISH);
            return writer.toString();
        } catch (IOException e) {
            logger.error("Error while rendering the {} page: {}", title, e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    // There is no transaction here, so DomainEventPublisher sends the event straight away
    // (fallbackExecution = true). A failure is only logged: the page is still returned.
    // entityId stays empty, a report is not a database row.
    private void publishReportExported(String actorEmail, String actorRole, String reportName,
                                       ReportResponseDto report, ReportSort sort, List<ReportSection> sections) {
        try {
            String details = reportName + ": from " + report.getFrom() + ", to " + report.getTo()
                    + ", groupBy " + report.getGroupBy() + ", sort " + sort + ", sections " + sections;
            eventPublisher.publishEvent(new DomainEvent("report.exported", actorEmail, actorRole,
                    "Report", null, details));
        } catch (Exception e) {
            logger.error("Could not publish the report.exported event for the {}: {}", reportName, e.getMessage());
        }
    }
}
