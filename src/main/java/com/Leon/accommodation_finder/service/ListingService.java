package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.exception.ListingNotFoundException;
import com.Leon.accommodation_finder.exception.DuplicateListingTitleException;
import com.Leon.accommodation_finder.exception.ListingOwnershipException;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.repository.LandlordRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListingService {

    private static final Logger logger = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository listingRepository;
    private final LandlordRepository landlordRepository;

    @Autowired
    public ListingService(ListingRepository listingRepository, LandlordRepository landlordRepository) {
        this.listingRepository = listingRepository;
        this.landlordRepository = landlordRepository;
    }

    public List<Listing> getAllListings() {
        try {
            return listingRepository.findAll();
        } catch (Exception e) {
            logger.error("Error while fetching all listings: {}", e.getMessage());
            throw e;
        }
    }

    public Listing getListingById(Long id) {
        try {
            return listingRepository.findById(id)
                    .orElseThrow(() -> new ListingNotFoundException(id));
        } catch (Exception e) {
            logger.error("Unexpected error while fetching listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // landlordEmail is the currently logged in landlord, taken from their JWT, never from the request body
    public Listing createListing(Listing listing, String landlordEmail) {
        try {
            if (listingRepository.existsByTitleIgnoreCase(listing.getTitle())) {
                throw new DuplicateListingTitleException(listing.getTitle());
            }

            Landlord landlord = landlordRepository.findByEmail(landlordEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "No landlord account found for email " + landlordEmail));

            listing.setLandlord(landlord);
            // status is system controlled, a new listing always starts as available
            listing.setStatus(ListingStatus.AVAILABLE);

            return listingRepository.save(listing);
        } catch (Exception e) {
            logger.error("Error while creating listing with title '{}': {}", listing.getTitle(), e.getMessage());
            throw e;
        }
    }

    public Listing updateListing(Long id, Listing updatedListing, String landlordEmail) {
        try {
            Listing listing = listingRepository.findById(id)
                    .orElseThrow(() -> new ListingNotFoundException(id));

            if (listing.getLandlord() == null || !listing.getLandlord().getEmail().equalsIgnoreCase(landlordEmail)) {
                throw new ListingOwnershipException();
            }

            listing.setTitle(updatedListing.getTitle());
            listing.setLocation(updatedListing.getLocation());
            listing.setPrice(updatedListing.getPrice());
            listing.setRoomType(updatedListing.getRoomType());
            listing.setContactInfo(updatedListing.getContactInfo());
            return listingRepository.save(listing);
        } catch (Exception e) {
            logger.error("Unexpected error while updating listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // isAdmin lets an admin delete any listing, a landlord can only delete their own
    public void deleteListing(Long id, String userEmail, boolean isAdmin) {
        try {
            Listing listing = listingRepository.findById(id)
                    .orElseThrow(() -> new ListingNotFoundException(id));

            boolean isOwner = listing.getLandlord() != null
                    && listing.getLandlord().getEmail().equalsIgnoreCase(userEmail);

            if (!isAdmin && !isOwner) {
                throw new ListingOwnershipException();
            }

            listingRepository.delete(listing);
        } catch (Exception e) {
            logger.error("Unexpected error while deleting listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }
}