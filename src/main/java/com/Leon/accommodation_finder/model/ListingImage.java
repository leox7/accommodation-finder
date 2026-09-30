package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// One photo of a listing. Only the generated file name is stored, the image itself lives on disk.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false)
    private String fileName;

    // a listing has at most one primary image and up to 4 secondary images
    private boolean isPrimary;
}
