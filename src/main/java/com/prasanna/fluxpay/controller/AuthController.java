package com.prasanna.fluxpay.controller;

import com.prasanna.fluxpay.audit.AuditService;
import com.prasanna.fluxpay.model.AppUser;
import com.prasanna.fluxpay.ratelimit.RateLimitService;
import com.prasanna.fluxpay.repository.UserRepository;
import com.prasanna.fluxpay.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          UserRepository userRepository, PasswordEncoder passwordEncoder,
                          RateLimitService rateLimitService, AuditService auditService) {
        this.authenticationManager = authenticationManager; this.jwtService = jwtService;
        this.userRepository = userRepository; this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService; this.auditService = auditService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkAuth(httpRequest, "register");
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUser user = userRepository.save(new AppUser(username, passwordEncoder.encode(request.password()), "USER"));
        auditService.record(username, "USER_REGISTERED", "user", user.getId().toString(), "New user registered");
        return new RegisterResponse("User registered successfully");
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkAuth(httpRequest, "login");
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        String token = jwtService.generateToken(request.username().trim());
        auditService.record(request.username().trim(), "USER_LOGIN", "user", null, "Successful login");
        return new LoginResponse(token, "Bearer");
    }

    public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Pattern(regexp = "^[A-Za-z0-9_.-]{3,50}$", message = "username must be 3-50 letters, numbers, dots, dashes or underscores")
        String username,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
        String password) {}
    public record LoginRequest(@NotBlank(message = "username is required") String username,
                               @NotBlank(message = "password is required") String password) {}
    public record LoginResponse(String token, String tokenType) {}
    public record RegisterResponse(String message) {}
}
