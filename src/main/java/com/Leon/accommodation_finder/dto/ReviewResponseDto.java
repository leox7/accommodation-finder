package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// What the API returns for a review. Reviews are public, so only the student's first name is shown,
// never their email or anything else about them.
@Getter
@Setter
@NoArgsConstructor
public class ReviewResponseDto {

    private Long id;
    private Long listingId;
    private String studentFirstName;
    private int rating;
    // null when the student left only stars
    private String comment;
    private LocalDateTime createdAt;
}
