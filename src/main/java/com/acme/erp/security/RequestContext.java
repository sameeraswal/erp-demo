package com.acme.erp.security;

import com.acme.erp.entity.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RequestContext {
    private UUID tenantId;
    private UUID userId;
    private String userEmail;
    private UserRole role;
}
