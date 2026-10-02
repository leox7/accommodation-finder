package com.Leon.accommodation_finder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// What a student sends to book a listing. Dates use the format yyyy-MM-dd.
// No student, status or dateCreated here, those are set by the server.
@Getter
@Setter
@NoArgsConstructor
public class BookingRequestDto {

    @NotNull(message = "Listing id is required")
    private Long listingId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;
}
