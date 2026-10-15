package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.ProfileResponseDto;
import com.Leon.accommodation_finder.dto.ProfileUpdateDto;
import com.Leon.accommodation_finder.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Any logged in user (student, landlord or admin) can see and edit their own profile.
// SecurityConfig already requires a token for everything under /api/users.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ProfileResponseDto getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getMyProfile(email);
    }

    @PatchMapping("/me")
    public ProfileResponseDto updateMyProfile(@Valid @RequestBody ProfileUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.updateMyProfile(email, dto);
    }
}
