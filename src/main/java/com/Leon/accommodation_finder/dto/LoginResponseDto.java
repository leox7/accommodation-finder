package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.Role;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class LoginResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String token;
    private String refreshToken;
    private String message;
}