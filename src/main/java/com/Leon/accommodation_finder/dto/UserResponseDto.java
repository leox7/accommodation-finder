package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class UserResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String message;

    // only filled in for landlords in the admin user list, always masked
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nationalIdNumber;
}