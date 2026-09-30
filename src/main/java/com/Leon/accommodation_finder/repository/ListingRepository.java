package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.model.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {
    boolean existsByTitleIgnoreCase(String title);

    // every filter is optional, a null parameter means "don't filter on this"
    @Query("SELECT l FROM Listing l WHERE " +
            "(:keyword IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(l.location) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:roomType IS NULL OR l.roomType = :roomType) " +
            "AND (:status IS NULL OR l.status = :status) " +
            "AND (:minPrice IS NULL OR l.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR l.price <= :maxPrice)")
    Page<Listing> search(@Param("keyword") String keyword,
                         @Param("roomType") RoomType roomType,
                         @Param("status") ListingStatus status,
                         @Param("minPrice") Double minPrice,
                         @Param("maxPrice") Double maxPrice,
                         Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE LOWER(l.landlord.email) = LOWER(:email)")
    Page<Listing> findMyListings(@Param("email") String email, Pageable pageable);
}
