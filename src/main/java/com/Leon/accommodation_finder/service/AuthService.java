package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.LandlordRegisterDto;
import com.Leon.accommodation_finder.dto.LoginDto;
import com.Leon.accommodation_finder.dto.LoginResponseDto;
import com.Leon.accommodation_finder.dto.StudentRegisterDto;
import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.exception.EmailAlreadyExistsException;
import com.Leon.accommodation_finder.exception.InvalidCredentialsException;
import com.Leon.accommodation_finder.exception.InvalidStudentEmailException;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.Role;
import com.Leon.accommodation_finder.model.Student;
import com.Leon.accommodation_finder.model.User;
import com.Leon.accommodation_finder.repository.LandlordRepository;
import com.Leon.accommodation_finder.repository.StudentRepository;
import com.Leon.accommodation_finder.repository.UserRepository;
import com.Leon.accommodation_finder.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private static final String STUDENT_EMAIL_DOMAIN = "@students.kcau.ac.ke";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final LandlordRepository landlordRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(UserRepository userRepository,
                       StudentRepository studentRepository,
                       LandlordRepository landlordRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.landlordRepository = landlordRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public UserResponseDto registerStudent(StudentRegisterDto dto) {
        try {
            String email = dto.getEmail().trim().toLowerCase();

            if (!email.endsWith(STUDENT_EMAIL_DOMAIN)) {
                throw new InvalidStudentEmailException(email);
            }
            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException(email);
            }

            Student student = new Student();
            student.setName(dto.getName());
            student.setEmail(email);
            student.setPassword(passwordEncoder.encode(dto.getPassword()));
            student.setUniversity(dto.getUniversity());
            // role is set by the system, never taken from the request
            student.setRole(Role.STUDENT);

            Student saved = studentRepository.save(student);
            return toUserResponse(saved, "Student account created successfully");
        } catch (Exception e) {
            logger.error("Error while registering student with email '{}': {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    public UserResponseDto registerLandlord(LandlordRegisterDto dto) {
        try {
            String email = dto.getEmail().trim().toLowerCase();

            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException(email);
            }

            Landlord landlord = new Landlord();
            landlord.setName(dto.getName());
            landlord.setEmail(email);
            landlord.setPassword(passwordEncoder.encode(dto.getPassword()));
            landlord.setNationalIdNumber(dto.getNationalIdNumber());
            landlord.setPhoneNumber(dto.getPhoneNumber());
            // a landlord stays unverified until they post their first listing
            landlord.setVerified(false);
            landlord.setRole(Role.LANDLORD);

            Landlord saved = landlordRepository.save(landlord);
            return toUserResponse(saved, "Landlord account created successfully");
        } catch (Exception e) {
            logger.error("Error while registering landlord with email '{}': {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    public LoginResponseDto login(LoginDto dto) {
        try {
            String email = dto.getEmail().trim().toLowerCase();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(InvalidCredentialsException::new);

            if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                throw new InvalidCredentialsException();
            }

            LoginResponseDto response = new LoginResponseDto();
            response.setId(user.getId());
            response.setName(user.getName());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());
            response.setToken(jwtUtil.generateToken(user));
            response.setMessage("Login successful");
            return response;
        } catch (Exception e) {
            logger.error("Error while logging in user with email '{}': {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    private UserResponseDto toUserResponse(User user, String message) {
        UserResponseDto response = new UserResponseDto();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setMessage(message);
        return response;
    }
}