package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// What the logged in student sees about their own review of one listing, so the page knows
// whether to show the review form, or their existing review with edit and delete buttons.
@Getter
@Setter
@NoArgsConstructor
public class MyReviewStatusDto {

    // true only when the student finished a stay in the listing and has not reviewed it yet
    private boolean canReview;
    // null when the student has not reviewed the listing. Its id marks "my review" in the public list
    private ReviewResponseDto myReview;
}
