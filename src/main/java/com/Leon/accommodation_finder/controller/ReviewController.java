package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.MyReviewStatusDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.ReviewRequestDto;
import com.Leon.accommodation_finder.dto.ReviewResponseDto;
import com.Leon.accommodation_finder.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

// Anyone can read the reviews of a listing (the GET is public, like the rest of GET /api/listings/**).
// Only a student who has finished a stay in the listing can write one.
@RestController
@RequestMapping("/api/listings/{listingId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @Autowired
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<ReviewResponseDto> createReview(@PathVariable Long listingId,
                                                          @Valid @RequestBody ReviewRequestDto request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        ReviewResponseDto created = reviewService.createReview(listingId, request, email);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    // does the logged in student have a review here, and if not, may they write one
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/mine")
    public MyReviewStatusDto getMyReviewStatus(@PathVariable Long listingId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return reviewService.getMyReviewStatus(listingId, email);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/{reviewId}")
    public ReviewResponseDto updateReview(@PathVariable Long listingId, @PathVariable Long reviewId,
                                          @Valid @RequestBody ReviewRequestDto request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return reviewService.updateReview(listingId, reviewId, request, email);
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @DeleteMapping("/{reviewId}")
    public void deleteReview(@PathVariable Long listingId, @PathVariable Long reviewId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        reviewService.deleteReview(listingId, reviewId, auth.getName(), isAdmin);
    }

    @GetMapping
    public PagedResponse<ReviewResponseDto> getListingReviews(
            @PathVariable Long listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.getListingReviews(listingId, page, size);
    }
}
