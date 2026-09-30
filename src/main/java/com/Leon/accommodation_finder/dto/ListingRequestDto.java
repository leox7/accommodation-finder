package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// What a landlord sends to create or update a listing.
// No id, status or landlord here, those are set by the server.
@Getter
@Setter
@NoArgsConstructor
public class ListingRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Location is required")
    private String location;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private Double price;

    @NotNull(message = "Room type is required")
    private RoomType roomType;

    @NotBlank(message = "Contact info is required")
    private String contactInfo;
}
