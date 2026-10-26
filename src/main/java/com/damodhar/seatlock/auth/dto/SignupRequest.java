package com.damodhar.seatlock.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName) {

    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", fullName=" + fullName + "]";
    }
}
