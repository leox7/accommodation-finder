package com.Leon.accommodation_finder.model;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Landlord extends User {

    @NotBlank(message = "National ID number is required")
    private String nationalIdNumber;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    private boolean verified = false;
}