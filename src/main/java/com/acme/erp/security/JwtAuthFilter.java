package com.acme.erp.security;

import com.acme.erp.entity.User;
import com.acme.erp.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final FilterErrorWriter filterErrorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/api/v1/bootstrap")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantHeader = request.getHeader("X-Tenant-Id");
            if (tenantHeader == null || tenantHeader.isBlank()) {
                filterErrorWriter.writeError(response, 400, "X-Tenant-Id header is required");
                return;
            }
            UUID tenantId = UUID.fromString(tenantHeader);

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterErrorWriter.writeError(response, 403, "Missing or invalid Authorization header");
                return;
            }

            Claims claims = jwtService.parseToken(authHeader.substring(7));
            UUID tokenTenantId = jwtService.getTenantId(claims);
            if (!tokenTenantId.equals(tenantId)) {
                filterErrorWriter.writeError(response, 403, "Tenant ID mismatch between header and token");
                return;
            }

            UUID userId = jwtService.getUserId(claims);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isActive() || !user.getTenant().getId().equals(tenantId)) {
                filterErrorWriter.writeError(response, 403, "User not authorized for this tenant");
                return;
            }

            RequestContext ctx = RequestContext.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .userEmail(jwtService.getEmail(claims))
                    .role(jwtService.getRole(claims))
                    .build();
            request.setAttribute("requestContext", ctx);

            var auth = new UsernamePasswordAuthenticationToken(
                    ctx, null, List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            filterErrorWriter.writeError(response, 400, e.getMessage());
        } catch (Exception e) {
            filterErrorWriter.writeError(response, 403, "Authentication failed");
        }
    }
}
