package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r WHERE r.listing.id = :listingId")
    Page<Review> findByListing(@Param("listingId") Long listingId, Pageable pageable);

    // used to enforce one review per student per listing
    @Query("SELECT COUNT(r) FROM Review r WHERE r.listing.id = :listingId AND LOWER(r.student.email) = LOWER(:email)")
    long countByListingAndStudent(@Param("listingId") Long listingId, @Param("email") String email);

    // the logged in student's own review of a listing, if any
    @Query("SELECT r FROM Review r WHERE r.listing.id = :listingId AND LOWER(r.student.email) = LOWER(:email)")
    Optional<Review> findByListingAndStudent(@Param("listingId") Long listingId, @Param("email") String email);

    long countByListingId(Long listingId);

    // null when the listing has no reviews yet
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listing.id = :listingId")
    Double findAverageRating(@Param("listingId") Long listingId);

    // reviews point at the listing, so they are removed when the listing is deleted
    void deleteByListingId(Long listingId);
}
