package com.Leon.accommodation_finder.config;

import com.Leon.accommodation_finder.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Autowired
    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Ties UserDetailsService (loads the user) and PasswordEncoder (checks the
    // password) together into the object that actually performs authentication.
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    // CORS lets the Angular app (another origin) call the API from the browser.
    // Only the one origin from application.properties is allowed, never a wildcard.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // the browser hides other response headers from Angular unless they are listed here,
        // and the frontend needs Retry-After to show "try again in 40 seconds" after a 429
        config.setExposedHeaders(List.of("Retry-After"));
        // the token travels in the Authorization header, so no cookies are needed
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // no server side sessions, the token carries the identity on every request
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // registering, logging in, refresh and logout must work without an access token
                        .requestMatchers("/api/auth/**").permitAll()
                        // Swagger's own page and the generated API description
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // a landlord's own listings need a token, this must stay above the public GET rule
                        .requestMatchers(HttpMethod.GET, "/api/listings/my").authenticated()
                        // browsing listings is open to everyone
                        .requestMatchers(HttpMethod.GET, "/api/listings/**").permitAll()
                        // listing photos must open in a browser or <img> tag without a token
                        .requestMatchers(HttpMethod.GET, "/uploads/listings/**").permitAll()
                        // the contact form is open to visitors who are not logged in
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").permitAll()
                        // Safaricom calls the M-Pesa callback without a token
                        .requestMatchers(HttpMethod.POST, "/api/payments/callback").permitAll()
                        // everything else needs a valid token
                        .anyRequest().authenticated())
                // a missing, expired or refresh token gives 401 (Spring's default would be 403)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}