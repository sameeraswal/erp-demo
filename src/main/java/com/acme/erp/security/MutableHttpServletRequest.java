package com.acme.erp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.*;

/** Allows injecting headers (e.g. X-Tenant-Id from query param for Swagger). */
public class MutableHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> headers = new HashMap<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        headers.put(name, value);
    }

    @Override
    public String getHeader(String name) {
        String value = headers.get(name);
        if (value != null) {
            return value;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>(headers.keySet());
        Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            names.add(original.nextElement());
        }
        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (headers.containsKey(name)) {
            return Collections.enumeration(List.of(headers.get(name)));
        }
        return super.getHeaders(name);
    }
}
