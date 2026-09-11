package com.genealogy.web;

import com.genealogy.domain.user.User;
import com.genealogy.security.JwtService;
import com.genealogy.store.UserStore;
import com.genealogy.web.dto.AuthRequest;
import com.genealogy.web.dto.AuthResponse;
import com.genealogy.web.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserStore userStore;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserStore userStore, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userStore = userStore;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("email is required"));
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid email format"));
        }

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        if (userStore.existsByEmail(email)) {
            return ResponseEntity.status(409).body(new ErrorResponse("email already registered"));
        }

        UUID userId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(password);
        userStore.createUser(userId, email, passwordHash);

        String token = jwtService.generateToken(userId);
        return ResponseEntity.status(201).body(new AuthResponse(userId.toString(), token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("email is required"));
        }

        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("password is required"));
        }

        Optional<User> userOpt = userStore.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(new ErrorResponse("invalid credentials"));
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(401).body(new ErrorResponse("invalid credentials"));
        }

        String token = jwtService.generateToken(user.getId());
        return ResponseEntity.ok(new AuthResponse(user.getId().toString(), token));
    }
}
