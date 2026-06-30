package com.acme.erp.service;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.Tenant;
import com.acme.erp.entity.User;
import com.acme.erp.exception.ForbiddenException;
import com.acme.erp.exception.NotFoundException;
import com.acme.erp.repository.TenantRepository;
import com.acme.erp.repository.UserRepository;
import com.acme.erp.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public Tenant validateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (!tenant.isActive()) {
            throw new ForbiddenException("Tenant is inactive");
        }
        return tenant;
    }

    public Dtos.TokenResponse login(UUID tenantId, Dtos.LoginRequest request) {
        validateTenant(tenantId);
        User user = userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())
                .orElseThrow(() -> new ForbiddenException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ForbiddenException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new ForbiddenException("User is inactive");
        }

        Dtos.TokenResponse response = new Dtos.TokenResponse();
        response.setAccessToken(jwtService.createToken(user));
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setTenantId(tenantId);
        return response;
    }
}
