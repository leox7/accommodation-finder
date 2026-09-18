package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.Role;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;
    private Role role;
    private String message;
}