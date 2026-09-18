package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.service.ListingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;

    @Autowired
    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public List<Listing> getAllListings() {
        return listingService.getAllListings();
    }

    @GetMapping("/{id}")
    public Listing getListingById(@PathVariable Long id) {
        return listingService.getListingById(id);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @PostMapping
    public ResponseEntity<Listing> createListing(@Valid @RequestBody Listing listing) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Listing created = listingService.createListing(listing, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @PutMapping("/{id}")
    public Listing updateListing(@PathVariable Long id, @RequestBody Listing listing) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return listingService.updateListing(id, listing, email);
    }

    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    @DeleteMapping("/{id}")
    public void deleteListing(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        listingService.deleteListing(id, email, isAdmin);
    }
}