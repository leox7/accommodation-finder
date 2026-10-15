package com.Leon.accommodation_finder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// The only things a user may change about themselves. Email, role and everything else are not here on purpose.
@Getter
@Setter
@NoArgsConstructor
public class ProfileUpdateDto {

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50, message = "First name must be 1 to 50 characters")
    @Pattern(regexp = "^[\\p{L} '\\-]+$", message = "First name can only have letters, spaces, hyphens and apostrophes")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50, message = "Last name must be 1 to 50 characters")
    @Pattern(regexp = "^[\\p{L} '\\-]+$", message = "Last name can only have letters, spaces, hyphens and apostrophes")
    private String lastName;
}
