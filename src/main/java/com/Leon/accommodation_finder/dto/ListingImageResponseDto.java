package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// Returned after an upload. url is the public link, never the path on disk.
@Getter
@Setter
@NoArgsConstructor
public class ListingImageResponseDto {

    private Long id;
    private String url;
    // Boolean (not boolean) so the JSON field is called "isPrimary" and not "primary"
    private Boolean isPrimary;
}
