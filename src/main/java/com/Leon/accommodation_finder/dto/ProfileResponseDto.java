package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// The logged in user's own profile. The first six fields are for every role,
// the others are filled in only for the role they belong to (null fields are left out of the JSON).
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private LocalDateTime createdAt;

    // students
    private String university;
    private Long activeBookings;
    private Long pendingRequests;
    private Long paymentsMade;

    // landlords. Phone number and national ID are always masked
    private String phoneNumber;
    private String nationalIdNumber;
    private Boolean verified;
    private Long listingCount;
}
