package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.BookingStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// What the API returns for a booking. Only a short summary of the listing is included,
// never the full Listing or Student entity.
@Getter
@Setter
@NoArgsConstructor
public class BookingResponseDto {

    private Long id;
    private Long listingId;
    private String listingTitle;
    private String listingLocation;
    // only the student's first name, so a landlord can see who is asking
    private String studentFirstName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BookingStatus status;
    private LocalDateTime dateCreated;
}
