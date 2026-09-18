package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {
    boolean existsByTitleIgnoreCase(String title);
}
