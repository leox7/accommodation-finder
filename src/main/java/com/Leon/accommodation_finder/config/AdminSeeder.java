package com.Leon.accommodation_finder.config;

import com.Leon.accommodation_finder.model.Admin;
import com.Leon.accommodation_finder.model.Role;
import com.Leon.accommodation_finder.repository.AdminRepository;
import com.Leon.accommodation_finder.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Runs once every time the app starts. Admins can only be created by another admin,
// so the very first one has to be created here, from the secrets file.
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email}")
    private String seedEmail;

    @Value("${admin.seed.password}")
    private String seedPassword;

    @Autowired
    public AdminSeeder(AdminRepository adminRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            if (adminRepository.count() > 0) {
                logger.info("An admin already exists, no admin was seeded");
                return;
            }

            String email = seedEmail.trim().toLowerCase();

            // a student or landlord may already use this email, saving would then break the unique email rule
            if (userRepository.existsByEmail(email)) {
                logger.error("Cannot seed the first admin, the seed email is already used by another account");
                return;
            }

            Admin admin = new Admin();
            admin.setFirstName("Dev");
            admin.setLastName("Admin");
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(seedPassword));
            admin.setRole(Role.ADMIN);
            adminRepository.save(admin);

            // the email and password come from the secrets file, so neither is logged
            logger.info("First admin account created");
        } catch (Exception e) {
            logger.error("Error while seeding the first admin: {}", e.getMessage());
            throw e;
        }
    }
}
