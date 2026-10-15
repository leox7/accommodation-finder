package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;

// The numbers on the home page: how many homes are available, and per room type how many and from what price.
@Getter
@Setter
@NoArgsConstructor
public class ListingSummaryDto {

    private long totalAvailable;
    // one row for every room type, also the ones with nothing available
    private List<RoomTypeSummaryDto> roomTypes;
}
