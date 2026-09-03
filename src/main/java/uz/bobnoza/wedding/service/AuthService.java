package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.auth.LoginRequest;
import uz.bobnoza.wedding.dto.auth.LoginResponse;
import uz.bobnoza.wedding.entity.Admin;
import uz.bobnoza.wedding.repository.AdminRepository;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AdminRepository adminRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, AdminRepository adminRepository,
                        JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.adminRepository = adminRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Throws BadCredentialsException (mapped to 401) on bad username/password
        // or a disabled account — handled centrally by GlobalExceptionHandler.
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        AdminPrincipal principal = (AdminPrincipal) authentication.getPrincipal();

        Admin admin = adminRepository.findById(principal.getAdminId()).orElseThrow();
        admin.setLastLoginAt(Instant.now());
        adminRepository.save(admin);

        String token = jwtService.generateToken(principal);
        String side = admin.getSide() != null ? admin.getSide().name() : null;
        return new LoginResponse(token, admin.getId(), admin.getUsername(), admin.getRole().name(), side);
    }
}
