package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.LandlordRegisterDto;
import com.Leon.accommodation_finder.dto.LoginDto;
import com.Leon.accommodation_finder.dto.LoginResponseDto;
import com.Leon.accommodation_finder.dto.OtpResponseDto;
import com.Leon.accommodation_finder.dto.RefreshTokenDto;
import com.Leon.accommodation_finder.dto.StudentRegisterDto;
import com.Leon.accommodation_finder.dto.UserResponseDto;
import com.Leon.accommodation_finder.dto.VerifyOtpDto;
import com.Leon.accommodation_finder.exception.EmailAlreadyExistsException;
import com.Leon.accommodation_finder.exception.InvalidCredentialsException;
import com.Leon.accommodation_finder.exception.InvalidOtpException;
import com.Leon.accommodation_finder.exception.InvalidRefreshTokenException;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
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
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final EmailService emailService;

    @Autowired
    public AuthService(UserRepository userRepository,
                       StudentRepository studentRepository,
                       LandlordRepository landlordRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       OtpService otpService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.landlordRepository = landlordRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.otpService = otpService;
        this.emailService = emailService;
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
            student.setFirstName(dto.getFirstName());
            student.setLastName(dto.getLastName());
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
            landlord.setFirstName(dto.getFirstName());
            landlord.setLastName(dto.getLastName());
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

    // Step one of login: checks the password, and if correct, emails an OTP
    // rather than returning a token. Nobody is fully logged in yet.
    public OtpResponseDto login(LoginDto dto) {
        try {
            String email = dto.getEmail().trim().toLowerCase();

            try {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(email, dto.getPassword()));
            } catch (AuthenticationException authEx) {
                throw new InvalidCredentialsException();
            }

            String otp = otpService.generateOtp(email);

            emailService.sendEmail(email, "Your Accommodation Finder login code",
                    "Your one time login code is " + otp + ". It expires in 5 minutes.");

            OtpResponseDto response = new OtpResponseDto();
            response.setEmail(email);
            response.setMessage("OTP sent to your email");
            return response;
        } catch (Exception e) {
            logger.error("Error while logging in user with email '{}': {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    // Step two of login: only once the correct OTP is provided does a token get issued.
    public LoginResponseDto verifyOtp(VerifyOtpDto dto) {
        try {
            String email = dto.getEmail().trim().toLowerCase();

            if (!otpService.isValid(email, dto.getOtp())) {
                throw new InvalidOtpException();
            }
            otpService.clearOtp(email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(InvalidCredentialsException::new);

            LoginResponseDto response = new LoginResponseDto();
            response.setId(user.getId());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());
            response.setToken(jwtUtil.generateAccessToken(user));
            response.setRefreshToken(jwtUtil.generateRefreshToken(user));
            response.setMessage("Login successful");
            return response;
        } catch (Exception e) {
            logger.error("Error while verifying OTP for email '{}': {}", dto.getEmail(), e.getMessage());
            throw e;
        }
    }

    // Uses a valid refresh token to issue a new access token, without needing
    // the password or OTP again. The refresh token itself is not reissued,
    // it stays valid until its own, longer expiry.
    public LoginResponseDto refreshToken(RefreshTokenDto dto) {
        try {
            String token = dto.getRefreshToken();

            if (!jwtUtil.isValid(token) || !jwtUtil.isRefreshToken(token)) {
                throw new InvalidRefreshTokenException();
            }

            String email = jwtUtil.getEmail(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(InvalidRefreshTokenException::new);

            LoginResponseDto response = new LoginResponseDto();
            response.setId(user.getId());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());
            response.setToken(jwtUtil.generateAccessToken(user));
            response.setMessage("Access token refreshed successfully");
            return response;
        } catch (Exception e) {
            logger.error("Error while refreshing token: {}", e.getMessage());
            throw e;
        }
    }

    private UserResponseDto toUserResponse(User user, String message) {
        UserResponseDto response = new UserResponseDto();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setMessage(message);
        return response;
    }
}