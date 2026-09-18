package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Landlord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LandlordRepository extends JpaRepository<Landlord, Long> {
    Optional<Landlord> findByEmail(String email);
}