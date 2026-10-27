package com.cartsync.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class IdempotencyKeyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("POST".equalsIgnoreCase(httpRequest.getMethod())
                && httpRequest.getRequestURI().startsWith("/orders")) {

            String idempotencyKey = httpRequest.getHeader("Idempotency-Key");

            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.getWriter().write("Missing Idempotency-Key header");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}