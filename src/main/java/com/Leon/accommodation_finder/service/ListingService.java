package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.ListingImageResponseDto;
import com.Leon.accommodation_finder.dto.ListingRequestDto;
import com.Leon.accommodation_finder.dto.ListingResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.exception.ListingNotFoundException;
import com.Leon.accommodation_finder.exception.DuplicateListingTitleException;
import com.Leon.accommodation_finder.exception.ListingOwnershipException;
import com.Leon.accommodation_finder.exception.ListingHasActiveBookingsException;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.model.ListingImage;
import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.model.RoomType;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.LandlordRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class ListingService {

    private static final Logger logger = LoggerFactory.getLogger(ListingService.class);

    private static final int MAX_PAGE_SIZE = 50;

    private final ListingRepository listingRepository;
    private final LandlordRepository landlordRepository;
    private final ListingImageService listingImageService;
    private final BookingRepository bookingRepository;

    @Autowired
    public ListingService(ListingRepository listingRepository, LandlordRepository landlordRepository,
                          ListingImageService listingImageService, BookingRepository bookingRepository) {
        this.listingRepository = listingRepository;
        this.landlordRepository = landlordRepository;
        this.listingImageService = listingImageService;
        this.bookingRepository = bookingRepository;
    }

    public PagedResponse<ListingResponseDto> searchListings(String keyword, RoomType roomType, ListingStatus status,
                                                            Double minPrice, Double maxPrice, int page, int size) {
        try {
            // a blank keyword means "no keyword filter"
            if (keyword != null && keyword.isBlank()) {
                keyword = null;
            }

            Page<Listing> listings = listingRepository.search(
                    keyword, roomType, status, minPrice, maxPrice, buildPageable(page, size));
            return PagedResponse.from(listings.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while searching listings: {}", e.getMessage());
            throw e;
        }
    }

    public PagedResponse<ListingResponseDto> getMyListings(String landlordEmail, int page, int size) {
        try {
            Page<Listing> listings = listingRepository.findMyListings(landlordEmail, buildPageable(page, size));
            return PagedResponse.from(listings.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching listings for the logged in landlord: {}", e.getMessage());
            throw e;
        }
    }

    public ListingResponseDto getListingById(Long id) {
        try {
            Listing listing = listingRepository.findById(id)
                    .orElseThrow(() -> new ListingNotFoundException(id));
            return toResponse(listing);
        } catch (Exception e) {
            logger.error("Unexpected error while fetching listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // landlordEmail is the currently logged in landlord, taken from their JWT, never from the request body
    public ListingResponseDto createListing(ListingRequestDto request, String landlordEmail) {
        try {
            if (listingRepository.existsByTitleIgnoreCase(request.getTitle())) {
                throw new DuplicateListingTitleException(request.getTitle());
            }

            Landlord landlord = landlordRepository.findByEmail(landlordEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "No landlord account found for email " + landlordEmail));

            Listing listing = new Listing();
            listing.setTitle(request.getTitle());
            listing.setDescription(request.getDescription());
            listing.setLocation(request.getLocation());
            listing.setPrice(request.getPrice());
            listing.setRoomType(request.getRoomType());
            listing.setContactInfo(request.getContactInfo());
            listing.setLandlord(landlord);
            // status is system controlled, a new listing always starts as available
            listing.setStatus(ListingStatus.AVAILABLE);

            return toResponse(listingRepository.save(listing));
        } catch (Exception e) {
            logger.error("Error while creating listing with title '{}': {}", request.getTitle(), e.getMessage());
            throw e;
        }
    }

    public ListingResponseDto updateListing(Long id, ListingRequestDto request, String landlordEmail) {
        try {
            Listing listing = findOwnedListing(id, landlordEmail);

            // only check for a duplicate when the title is actually changing,
            // otherwise the listing would clash with its own title
            boolean titleChanged = !listing.getTitle().equalsIgnoreCase(request.getTitle());
            if (titleChanged && listingRepository.existsByTitleIgnoreCase(request.getTitle())) {
                throw new DuplicateListingTitleException(request.getTitle());
            }

            listing.setTitle(request.getTitle());
            listing.setDescription(request.getDescription());
            listing.setLocation(request.getLocation());
            listing.setPrice(request.getPrice());
            listing.setRoomType(request.getRoomType());
            listing.setContactInfo(request.getContactInfo());
            return toResponse(listingRepository.save(listing));
        } catch (Exception e) {
            logger.error("Unexpected error while updating listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // a simple manual override, bookings are still decided by date overlap, not by this flag
    public ListingResponseDto updateListingStatus(Long id, ListingStatus status, String landlordEmail) {
        try {
            Listing listing = findOwnedListing(id, landlordEmail);
            listing.setStatus(status);
            return toResponse(listingRepository.save(listing));
        } catch (Exception e) {
            logger.error("Error while updating status of listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    public ListingImageResponseDto uploadImage(Long id, MultipartFile file, boolean isPrimary, String landlordEmail) {
        try {
            Listing listing = findOwnedListing(id, landlordEmail);
            return listingImageService.saveImage(listing, file, isPrimary);
        } catch (Exception e) {
            logger.error("Error while uploading image for listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    public void deleteImage(Long id, Long imageId, String landlordEmail) {
        try {
            findOwnedListing(id, landlordEmail);
            listingImageService.deleteImage(id, imageId);
        } catch (Exception e) {
            logger.error("Error while deleting image {} of listing with id {}: {}", imageId, id, e.getMessage());
            throw e;
        }
    }

    // isAdmin lets an admin delete any listing, a landlord can only delete their own.
    // Transactional because the booking rows, image rows and the listing row are deleted together.
    @Transactional
    public void deleteListing(Long id, String userEmail, boolean isAdmin) {
        try {
            Listing listing = listingRepository.findById(id)
                    .orElseThrow(() -> new ListingNotFoundException(id));

            boolean isOwner = listing.getLandlord() != null
                    && listing.getLandlord().getEmail().equalsIgnoreCase(userEmail);

            if (!isAdmin && !isOwner) {
                throw new ListingOwnershipException();
            }

            // a listing with pending or confirmed bookings must stay, students are relying on it
            if (bookingRepository.existsByListingIdAndStatusIn(
                    id, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
                throw new ListingHasActiveBookingsException();
            }

            // bookings and images point at the listing, so they must be removed first.
            // Only cancelled or vacated bookings can be left at this point.
            bookingRepository.deleteByListingId(id);
            listingImageService.deleteAllImages(id);
            listingRepository.delete(listing);
        } catch (Exception e) {
            logger.error("Unexpected error while deleting listing with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // the listing must exist (404) and belong to the logged in landlord (403)
    private Listing findOwnedListing(Long id, String landlordEmail) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException(id));

        if (listing.getLandlord() == null || !listing.getLandlord().getEmail().equalsIgnoreCase(landlordEmail)) {
            throw new ListingOwnershipException();
        }
        return listing;
    }

    // page is at least 0, size is between 1 and 50, newest listings first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    // only the landlord's first name is copied, never their national ID, phone number or email
    private ListingResponseDto toResponse(Listing listing) {
        ListingResponseDto response = new ListingResponseDto();
        response.setId(listing.getId());
        response.setTitle(listing.getTitle());
        response.setDescription(listing.getDescription());
        response.setLocation(listing.getLocation());
        response.setPrice(listing.getPrice());
        response.setRoomType(listing.getRoomType());
        response.setContactInfo(listing.getContactInfo());
        response.setStatus(listing.getStatus());
        if (listing.getLandlord() != null) {
            response.setLandlordFirstName(listing.getLandlord().getFirstName());
        }

        List<String> secondaryImageUrls = new ArrayList<>();
        for (ListingImage image : listingImageService.getImages(listing.getId())) {
            if (image.isPrimary()) {
                response.setPrimaryImageUrl(listingImageService.getImageUrl(image));
            } else {
                secondaryImageUrls.add(listingImageService.getImageUrl(image));
            }
        }
        response.setSecondaryImageUrls(secondaryImageUrls);
        return response;
    }
}
