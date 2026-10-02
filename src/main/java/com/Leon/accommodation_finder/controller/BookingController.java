package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.BookingRequestDto;
import com.Leon.accommodation_finder.dto.BookingResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

// Students create, view, cancel and vacate their own bookings.
// Landlords view and confirm bookings on their own listings.
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    @Autowired
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<BookingResponseDto> createBooking(@Valid @RequestBody BookingRequestDto request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        BookingResponseDto created = bookingService.createBooking(request, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public PagedResponse<BookingResponseDto> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingService.getMyBookings(email, page, size);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/{id}/cancel")
    public BookingResponseDto cancelBooking(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingService.cancelBooking(id, email);
    }

    // a student leaves a confirmed stay
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/{id}/vacate")
    public BookingResponseDto vacateBooking(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingService.vacateBooking(id, email);
    }

    // bookings on the logged in landlord's own listings
    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping("/landlord")
    public PagedResponse<BookingResponseDto> getLandlordBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingService.getLandlordBookings(email, page, size);
    }

    // only the landlord who owns the booking's listing can confirm it
    @PreAuthorize("hasRole('LANDLORD')")
    @PutMapping("/{id}/confirm")
    public BookingResponseDto confirmBooking(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return bookingService.confirmBooking(id, email);
    }
}
