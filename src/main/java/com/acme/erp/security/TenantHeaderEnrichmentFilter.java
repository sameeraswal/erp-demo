package com.acme.erp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Swagger UI sometimes fails to send X-Tenant-Id as a header.
 * Accept tenant_id query param as a fallback and promote it to a header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantHeaderEnrichmentFilter extends OncePerRequestFilter {

  static final String TENANT_HEADER = "X-Tenant-Id";
  static final String TENANT_QUERY_PARAM = "tenant_id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    String tenant = request.getHeader(TENANT_HEADER);
    if (tenant == null || tenant.isBlank()) {
      tenant = request.getParameter(TENANT_QUERY_PARAM);
    }
    if (tenant != null && !tenant.isBlank()) {
      MutableHttpServletRequest wrapped = new MutableHttpServletRequest(request);
      wrapped.putHeader(TENANT_HEADER, tenant.trim());
      filterChain.doFilter(wrapped, response);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
