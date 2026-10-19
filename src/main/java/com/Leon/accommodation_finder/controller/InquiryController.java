package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.InquiryRequestDto;
import com.Leon.accommodation_finder.dto.InquiryResponseDto;
import com.Leon.accommodation_finder.service.InquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The public contact form. Listed as public in SecurityConfig. The admin list is in AdminController.
@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    @Autowired
    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @PostMapping
    public ResponseEntity<InquiryResponseDto> createInquiry(@Valid @RequestBody InquiryRequestDto dto,
                                                            HttpServletRequest request) {
        // the address of the caller. Behind a proxy in production this needs the proxy's forwarded address setting
        InquiryResponseDto created = inquiryService.createInquiry(dto, request.getRemoteAddr());
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
