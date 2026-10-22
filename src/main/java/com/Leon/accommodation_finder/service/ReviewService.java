package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.MyReviewStatusDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.ReviewRequestDto;
import com.Leon.accommodation_finder.dto.ReviewResponseDto;
import com.Leon.accommodation_finder.exception.ListingNotFoundException;
import com.Leon.accommodation_finder.exception.ReviewAlreadyExistsException;
import com.Leon.accommodation_finder.exception.ReviewNotAllowedException;
import com.Leon.accommodation_finder.exception.ReviewNotFoundException;
import com.Leon.accommodation_finder.exception.ReviewOwnershipException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.model.Review;
import com.Leon.accommodation_finder.model.Student;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import com.Leon.accommodation_finder.repository.ReviewRepository;
import com.Leon.accommodation_finder.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);

    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final ListingRepository listingRepository;
    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository, ListingRepository listingRepository,
                         StudentRepository studentRepository, BookingRepository bookingRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.listingRepository = listingRepository;
        this.studentRepository = studentRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    // studentEmail is the currently logged in student, taken from their JWT, never from the request body
    public ReviewResponseDto createReview(Long listingId, ReviewRequestDto request, String studentEmail) {
        try {
            Listing listing = listingRepository.findById(listingId)
                    .orElseThrow(() -> new ListingNotFoundException(listingId));

            if (reviewRepository.countByListingAndStudent(listingId, studentEmail) > 0) {
                throw new ReviewAlreadyExistsException();
            }

            // only a student who has finished a stay in this listing may review it
            if (bookingRepository.countFinishedStays(listingId, studentEmail, LocalDate.now()) == 0) {
                throw new ReviewNotAllowedException();
            }

            Student student = studentRepository.findByEmail(studentEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "No student account found for email " + studentEmail));

            Review review = new Review();
            review.setStudent(student);
            review.setListing(listing);
            review.setRating(request.getRating());
            review.setComment(cleanComment(request.getComment()));
            review.setDateCreated(LocalDateTime.now());

            Review saved;
            try {
                saved = reviewRepository.save(review);
            } catch (DataIntegrityViolationException e) {
                // two requests at the same moment: the unique constraint let only one through
                throw new ReviewAlreadyExistsException();
            }

            // the comment text is not put in the event, only the rating
            eventPublisher.publishEvent(new DomainEvent("review.created", studentEmail, "STUDENT",
                    "Review", saved.getId(), "Rated listing " + listingId + " " + saved.getRating() + " stars"));
            return toResponse(saved);
        } catch (Exception e) {
            logger.error("Error while creating review for listing with id {}: {}", listingId, e.getMessage());
            throw e;
        }
    }

    public PagedResponse<ReviewResponseDto> getListingReviews(Long listingId, int page, int size) {
        try {
            if (!listingRepository.existsById(listingId)) {
                throw new ListingNotFoundException(listingId);
            }
            Page<Review> reviews = reviewRepository.findByListing(listingId, buildPageable(page, size));
            return PagedResponse.from(reviews.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching reviews of listing with id {}: {}", listingId, e.getMessage());
            throw e;
        }
    }

    // tells the logged in student if they may review the listing, and gives back their review if they already did
    public MyReviewStatusDto getMyReviewStatus(Long listingId, String studentEmail) {
        try {
            if (!listingRepository.existsById(listingId)) {
                throw new ListingNotFoundException(listingId);
            }

            MyReviewStatusDto status = new MyReviewStatusDto();
            Optional<Review> myReview = reviewRepository.findByListingAndStudent(listingId, studentEmail);
            if (myReview.isPresent()) {
                status.setMyReview(toResponse(myReview.get()));
            } else {
                status.setCanReview(
                        bookingRepository.countFinishedStays(listingId, studentEmail, LocalDate.now()) > 0);
            }
            return status;
        } catch (Exception e) {
            logger.error("Error while checking the review status for listing with id {}: {}", listingId, e.getMessage());
            throw e;
        }
    }

    // a student can change the rating and comment of their own review
    public ReviewResponseDto updateReview(Long listingId, Long reviewId, ReviewRequestDto request, String studentEmail) {
        try {
            Review review = findReviewOnListing(listingId, reviewId);

            if (!review.getStudent().getEmail().equalsIgnoreCase(studentEmail)) {
                throw new ReviewOwnershipException();
            }

            review.setRating(request.getRating());
            review.setComment(cleanComment(request.getComment()));
            Review saved = reviewRepository.save(review);

            eventPublisher.publishEvent(new DomainEvent("review.updated", studentEmail, "STUDENT",
                    "Review", saved.getId(), "Rated listing " + listingId + " " + saved.getRating() + " stars"));
            return toResponse(saved);
        } catch (Exception e) {
            logger.error("Error while updating review with id {}: {}", reviewId, e.getMessage());
            throw e;
        }
    }

    // isAdmin lets an admin delete any review, a student can only delete their own
    public void deleteReview(Long listingId, Long reviewId, String userEmail, boolean isAdmin) {
        try {
            Review review = findReviewOnListing(listingId, reviewId);

            if (!isAdmin && !review.getStudent().getEmail().equalsIgnoreCase(userEmail)) {
                throw new ReviewOwnershipException();
            }

            reviewRepository.delete(review);

            eventPublisher.publishEvent(new DomainEvent("review.deleted", userEmail, isAdmin ? "ADMIN" : "STUDENT",
                    "Review", reviewId, "Review of listing " + listingId + " deleted"));
        } catch (Exception e) {
            logger.error("Error while deleting review with id {}: {}", reviewId, e.getMessage());
            throw e;
        }
    }

    // the review must exist and belong to the listing in the URL (404 otherwise)
    private Review findReviewOnListing(Long listingId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        if (!review.getListing().getId().equals(listingId)) {
            throw new ReviewNotFoundException(reviewId);
        }
        return review;
    }

    // a blank comment is saved as nothing
    private String cleanComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    // page is at least 0, size is between 1 and 50, newest reviews first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    // only the student's first name is copied, never their email
    private ReviewResponseDto toResponse(Review review) {
        ReviewResponseDto response = new ReviewResponseDto();
        response.setId(review.getId());
        response.setListingId(review.getListing().getId());
        response.setStudentFirstName(review.getStudent().getFirstName());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getDateCreated());
        return response;
    }
}
