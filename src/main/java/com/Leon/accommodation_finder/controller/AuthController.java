package com.Leon.accommodation_finder.controller;

import com.Leon.accommodation_finder.dto.LandlordRegisterDto;
import com.Leon.accommodation_finder.dto.LoginDto;
import com.Leon.accommodation_finder.dto.LoginResponseDto;
import com.Leon.accommodation_finder.dto.StudentRegisterDto;
import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/student")
    public ResponseEntity<UserResponseDto> registerStudent(@Valid @RequestBody StudentRegisterDto dto) {
        UserResponseDto created = authService.registerStudent(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/register/landlord")
    public ResponseEntity<UserResponseDto> registerLandlord(@Valid @RequestBody LandlordRegisterDto dto) {
        UserResponseDto created = authService.registerLandlord(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginDto dto) {
        return authService.login(dto);
    }
}
