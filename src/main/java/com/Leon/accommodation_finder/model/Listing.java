package com.Leon.accommodation_finder.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Column(unique = true)
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Location is required")
    private String location;

    @Positive(message = "Price must be greater than zero")
    private Double price;

    @NotNull(message = "Room type is required")
    @Enumerated(EnumType.STRING)
    private RoomType roomType;

    @NotBlank(message = "Contact info is required")
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    private ListingStatus status = ListingStatus.AVAILABLE;

    @ManyToOne
    @JoinColumn(name = "landlord_id")
    private Landlord landlord;
}