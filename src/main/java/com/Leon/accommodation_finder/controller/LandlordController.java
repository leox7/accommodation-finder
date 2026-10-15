package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.LandlordStatsDto;
import com.Leon.accommodation_finder.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/landlord")
public class LandlordController {

    private final StatsService statsService;

    @Autowired
    public LandlordController(StatsService statsService) {
        this.statsService = statsService;
    }

    // the numbers on the landlord dashboard
    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping("/stats")
    public LandlordStatsDto getStats() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return statsService.getLandlordStats(email);
    }
}
