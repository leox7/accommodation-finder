package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {

    List<ListingImage> findByListingIdOrderByIdAsc(Long listingId);

    // used for the 404 check, an image id only counts if it belongs to that listing
    Optional<ListingImage> findByIdAndListingId(Long id, Long listingId);

    // isPrimary = true counts the primary image, false counts the secondary images
    @Query("SELECT COUNT(i) FROM ListingImage i WHERE i.listing.id = :listingId AND i.isPrimary = :isPrimary")
    long countImages(@Param("listingId") Long listingId, @Param("isPrimary") boolean isPrimary);
}
