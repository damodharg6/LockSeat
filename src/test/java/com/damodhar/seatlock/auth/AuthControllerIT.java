package com.damodhar.seatlock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.damodhar.seatlock.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class AuthControllerIT extends AbstractIntegrationTest {

    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void signupCreatesBuyerAndNeverReturnsPassword() throws Exception {
        String email = uniqueEmail();

        signup(email.toUpperCase(), PASSWORD, "Asha Rao")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/me"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIs409IgnoringCase() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "First").andExpect(status().isCreated());

        signup(email.toUpperCase(), PASSWORD, "Second")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
    }

    @Test
    void invalidSignupIs400() throws Exception {
        signup("not-an-email", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
        signup(uniqueEmail(), "x".repeat(73), "Long Password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginReturnsBearerTokenValidForFifteenMinutes() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "Asha").andExpect(status().isCreated());

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetIdentical401() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "Asha").andExpect(status().isCreated());

        String wrongPassword = login(email, "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login(uniqueEmail(), "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(unknownEmail).get("detail"))
                .isEqualTo(json.readTree(wrongPassword).get("detail"));
    }

    @Test
    void meReturnsCurrentUserWithToken() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "Asha").andExpect(status().isCreated());
        String token = accessToken(email);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meWithoutTokenIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://seatlock.dev/problems/unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String expired = sign(jwtEncoder, JwtClaimsSet.builder()
                .issuer("seatlock").subject("1").issuedAt(past).expiresAt(past.plusSeconds(900))
                .claim("role", "ADMIN").build());

        expectInvalidToken(expired);
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "Asha").andExpect(status().isCreated());
        String[] parts = accessToken(email).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"BUYER\"", "\"ADMIN\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        expectInvalidToken(tampered);
    }

    @Test
    void tokenSignedWithAnotherKeyIs401() throws Exception {
        var otherKey = new SecretKeySpec("some-other-secret-that-is-32-bytes-long!".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        String forged = sign(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), JwtClaimsSet.builder()
                .issuer("seatlock").subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("role", "ADMIN").build());

        expectInvalidToken(forged);
    }

    @Test
    void garbageTokenIs401() throws Exception {
        expectInvalidToken("not.a.jwt");
    }

    void expectInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or expired."));
    }

    static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    String accessToken(String email) throws Exception {
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    ResultActions signup(String email, String password, String fullName) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password, "fullName", fullName));
        return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
