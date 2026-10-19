package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.ProfileResponseDto;
import com.Leon.accommodation_finder.dto.ProfileUpdateDto;
import com.Leon.accommodation_finder.exception.UserNotFoundException;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.Student;
import com.Leon.accommodation_finder.model.User;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import com.Leon.accommodation_finder.repository.PaymentRepository;
import com.Leon.accommodation_finder.repository.UserRepository;
import com.Leon.accommodation_finder.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// The logged in user's own profile. The email always comes from the JWT, never from the request.
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;

    @Autowired
    public UserService(UserRepository userRepository, BookingRepository bookingRepository,
                       PaymentRepository paymentRepository, ListingRepository listingRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.listingRepository = listingRepository;
    }

    public ProfileResponseDto getMyProfile(String email) {
        try {
            return toProfile(findUser(email));
        } catch (Exception e) {
            logger.error("Error while fetching the profile of the logged in user: {}", e.getMessage());
            throw e;
        }
    }

    // only the first and last name can change here
    public ProfileResponseDto updateMyProfile(String email, ProfileUpdateDto dto) {
        try {
            User user = findUser(email);
            user.setFirstName(dto.getFirstName().trim());
            user.setLastName(dto.getLastName().trim());
            userRepository.save(user);
            return toProfile(user);
        } catch (Exception e) {
            logger.error("Error while updating the profile of the logged in user: {}", e.getMessage());
            throw e;
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
    }

    // common fields for everyone, then the extra fields for the user's own role
    private ProfileResponseDto toProfile(User user) {
        ProfileResponseDto profile = new ProfileResponseDto();
        profile.setId(user.getId());
        profile.setFirstName(user.getFirstName());
        profile.setLastName(user.getLastName());
        profile.setEmail(user.getEmail());
        profile.setRole(user.getRole());
        profile.setCreatedAt(user.getDateCreated());

        if (user instanceof Student student) {
            profile.setUniversity(student.getUniversity());
            profile.setActiveBookings(bookingRepository.countStudentBookingsWithStatus(user.getEmail(), BookingStatus.CONFIRMED));
            profile.setPendingRequests(bookingRepository.countStudentBookingsWithStatus(user.getEmail(), BookingStatus.PENDING));
            profile.setPaymentsMade(paymentRepository.countStudentPaidPayments(user.getEmail()));
        } else if (user instanceof Landlord landlord) {
            profile.setPhoneNumber(MaskingUtil.maskPhoneNumber(landlord.getPhoneNumber()));
            profile.setNationalIdNumber(MaskingUtil.maskNationalId(landlord.getNationalIdNumber()));
            profile.setVerified(landlord.isVerified());
            // a null status counts all of this landlord's listings
            profile.setListingCount(listingRepository.countLandlordListings(user.getEmail(), null));
        }
        return profile;
    }
}
