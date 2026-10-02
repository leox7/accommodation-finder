package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.model.RoomType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;

// What the API returns for a listing. Only the landlord's first name is shown,
// never their national ID, phone number or email.
@Getter
@Setter
@NoArgsConstructor
public class ListingResponseDto {

    private Long id;
    private String title;
    private String description;
    private String location;
    private Double price;
    private RoomType roomType;
    private String contactInfo;
    private ListingStatus status;
    private String landlordFirstName;
    // null when the listing has no primary image yet
    private String primaryImageUrl;
    private List<String> secondaryImageUrls;
}
