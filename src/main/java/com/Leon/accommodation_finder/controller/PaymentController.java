package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.MpesaCallbackDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.PaymentRequestDto;
import com.Leon.accommodation_finder.dto.PaymentResponseDto;
import com.Leon.accommodation_finder.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// Students pay the deposit and rent for their own confirmed bookings and see their own payments.
// Landlords see payments made on their own listings. Admins can view any single payment.
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(@Valid @RequestBody PaymentRequestDto request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PaymentResponseDto created = paymentService.createPayment(request, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public PagedResponse<PaymentResponseDto> getMyPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return paymentService.getMyPayments(email, page, size);
    }

    // payments on the logged in landlord's own listings
    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping("/received")
    public PagedResponse<PaymentResponseDto> getReceivedPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return paymentService.getReceivedPayments(email, page, size);
    }

    // Safaricom calls this (through ngrok) when an STK Push finishes. It is public because Safaricom
    // has no token, and the path never contains the word mpesa. It ALWAYS answers 200 with
    // ResultCode 0, even for a callback that is ignored, otherwise Safaricom keeps retrying.
    @PostMapping("/callback")
    public Map<String, Object> mpesaCallback(@RequestBody(required = false) MpesaCallbackDto callback) {
        paymentService.handleMpesaCallback(callback);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ResultCode", 0);
        response.put("ResultDesc", "Accepted");
        return response;
    }

    // the frontend will poll this to see when a payment is PAID or FAILED
    @PreAuthorize("hasAnyRole('STUDENT', 'LANDLORD', 'ADMIN')")
    @GetMapping("/{id}")
    public PaymentResponseDto getPayment(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return paymentService.getPayment(id, email, isAdmin);
    }
}
