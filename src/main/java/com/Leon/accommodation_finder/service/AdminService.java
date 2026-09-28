package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.model.User;
import com.Leon.accommodation_finder.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;

    @Autowired
    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponseDto> getAllUsers() {
        try {
            List<UserResponseDto> users = new ArrayList<>();

            for (User user : userRepository.findAll()) {
                UserResponseDto dto = new UserResponseDto();
                dto.setId(user.getId());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setEmail(user.getEmail());
                dto.setRole(user.getRole());
                users.add(dto);
            }

            return users;
        } catch (Exception e) {
            logger.error("Error while fetching all users: {}", e.getMessage());
            throw e;
        }
    }
}