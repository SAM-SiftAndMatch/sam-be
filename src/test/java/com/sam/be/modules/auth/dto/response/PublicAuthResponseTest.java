package com.sam.be.modules.auth.dto.response;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PublicAuthResponseTest {
    @Test
    void refreshTokenNeverAppearsInWebResponse() throws Exception {
        AuthResponse internal =
                AuthResponse.builder()
                        .accessToken("sam-access-token")
                        .refreshToken("private-refresh-token")
                        .tokenType("Bearer")
                        .build();

        String json = new ObjectMapper().writeValueAsString(PublicAuthResponse.from(internal));

        assertTrue(json.contains("sam-access-token"));
        assertFalse(json.contains("private-refresh-token"));
        assertFalse(json.contains("refreshToken"));
    }
}
