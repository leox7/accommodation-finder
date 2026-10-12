package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// The admin's view of a booking: everything in BookingResponseDto, plus who the student is.
// Only the name and email are added, never the student's phone number or any ID number.
@Getter
@Setter
@NoArgsConstructor
public class AdminBookingResponseDto extends BookingResponseDto {

    private String studentLastName;
    private String studentEmail;
}
