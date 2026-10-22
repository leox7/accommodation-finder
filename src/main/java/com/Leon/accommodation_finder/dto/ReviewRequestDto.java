package com.Leon.accommodation_finder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// What a student sends to review a listing. No student, listing or date here, the server sets those.
@Getter
@Setter
@NoArgsConstructor
public class ReviewRequestDto {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    // optional, a student may leave only stars
    @Size(max = 500, message = "Comment must be at most 500 characters")
    private String comment;
}
