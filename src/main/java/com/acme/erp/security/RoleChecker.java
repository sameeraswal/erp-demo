package com.acme.erp.security;

import com.acme.erp.entity.enums.UserRole;
import com.acme.erp.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RoleChecker {

    public RequestContext getContext(HttpServletRequest request) {
        Object ctx = request.getAttribute("requestContext");
        if (ctx == null) {
            throw new ForbiddenException("Not authenticated");
        }
        return (RequestContext) ctx;
    }

    public RequestContext requireRole(HttpServletRequest request, UserRole... roles) {
        RequestContext ctx = getContext(request);
        Set<UserRole> allowed = Arrays.stream(roles).collect(Collectors.toSet());
        if (!allowed.contains(ctx.getRole()) && ctx.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Insufficient permissions. Required: " + allowed);
        }
        return ctx;
    }
}
