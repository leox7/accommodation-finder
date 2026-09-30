package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.ListingImageResponseDto;
import com.Leon.accommodation_finder.dto.ListingRequestDto;
import com.Leon.accommodation_finder.dto.ListingResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.model.RoomType;
import com.Leon.accommodation_finder.service.ListingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;

    @Autowired
    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    // all filters are optional, e.g. /api/listings?keyword=madaraka&roomType=STUDIO&maxPrice=15000
    @GetMapping
    public PagedResponse<ListingResponseDto> getAllListings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RoomType roomType,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return listingService.searchListings(keyword, roomType, status, minPrice, maxPrice, page, size);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @GetMapping("/my")
    public PagedResponse<ListingResponseDto> getMyListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return listingService.getMyListings(email, page, size);
    }

    @GetMapping("/{id}")
    public ListingResponseDto getListingById(@PathVariable Long id) {
        return listingService.getListingById(id);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @PostMapping
    public ResponseEntity<ListingResponseDto> createListing(@Valid @RequestBody ListingRequestDto request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        ListingResponseDto created = listingService.createListing(request, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @PutMapping("/{id}")
    public ListingResponseDto updateListing(@PathVariable Long id, @Valid @RequestBody ListingRequestDto request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return listingService.updateListing(id, request, email);
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

    // upload is a separate step after the listing exists, one image per request
    @PreAuthorize("hasRole('LANDLORD')")
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingImageResponseDto> uploadImage(@PathVariable Long id,
                                                               @RequestParam MultipartFile file,
                                                               @RequestParam(defaultValue = "false") boolean isPrimary) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        ListingImageResponseDto created = listingService.uploadImage(id, file, isPrimary, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('LANDLORD')")
    @DeleteMapping("/{id}/images/{imageId}")
    public void deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        listingService.deleteImage(id, imageId, email);
    }
}
