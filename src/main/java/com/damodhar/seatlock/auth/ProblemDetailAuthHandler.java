package com.damodhar.seatlock.auth;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.damodhar.seatlock.common.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Security failures happen in the filter chain, before any controller, so GlobalExceptionHandler
 * never sees them. This writes the same RFC 9457 ProblemDetail shape for 401 and 403.
 */
@Component
public class ProblemDetailAuthHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        boolean badToken = ex instanceof InvalidBearerTokenException;
        String detail = badToken ? "The access token is invalid or expired." : "Authentication is required.";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(request, response, GlobalExceptionHandler.problem(HttpStatus.UNAUTHORIZED, "unauthorized",
                "Unauthorized", detail));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, GlobalExceptionHandler.problem(HttpStatus.FORBIDDEN, "forbidden",
                "Forbidden", "You do not have permission to do this."));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
