package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.ListingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// What a landlord sends to manually mark their listing AVAILABLE or OCCUPIED.
@Getter
@Setter
@NoArgsConstructor
public class ListingStatusRequestDto {

    @NotNull(message = "Status is required")
    private ListingStatus status;
}
