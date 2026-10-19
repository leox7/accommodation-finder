package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.RoomType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class RoomTypeSummaryDto {

    private RoomType roomType;
    private long availableCount;
    // null when no listing of this type is available
    private Double lowestPrice;
}
