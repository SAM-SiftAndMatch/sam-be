package com.sam.be.common.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.security.config.JwtAuthenticationConverter;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedStompInterceptorTest {
    private Message<byte[]> frame(
            StompCommand command, String destination, JwtAuthenticationToken user) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        if (user != null) headers.setUser(user);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void anonymousCannotSubscribeOrSend() {
        var interceptor =
                new AuthenticatedStompInterceptor(
                        mock(CustomJwtDecoder.class),
                        mock(JwtAuthenticationConverter.class),
                        mock(JdbcTemplate.class));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                frame(
                                        StompCommand.SUBSCRIBE,
                                        "/topic/chat/" + UUID.randomUUID(),
                                        null),
                                mock(MessageChannel.class)));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                frame(
                                        StompCommand.SEND,
                                        "/app/chat/" + UUID.randomUUID() + "/send",
                                        null),
                                mock(MessageChannel.class)));
    }

    @Test
    void ownNotificationsOnlyAndUnknownTopicsFailClosed() {
        UUID id = UUID.randomUUID();
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "HS512")
                        .claim("user_id", id.toString())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        var user = new JwtAuthenticationToken(jwt, List.of());
        var decoder = mock(CustomJwtDecoder.class);
        when(decoder.decode("token")).thenReturn(jwt);
        var converter = mock(JwtAuthenticationConverter.class);
        when(converter.convert(jwt)).thenReturn(user);
        var interceptor =
                new AuthenticatedStompInterceptor(decoder, converter, mock(JdbcTemplate.class));
        var channel = mock(MessageChannel.class);
        assertNotNull(
                interceptor.preSend(
                        frame(
                                StompCommand.SUBSCRIBE,
                                "/topic/users/" + id + "/notifications",
                                user),
                        channel));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                frame(
                                        StompCommand.SUBSCRIBE,
                                        "/topic/users/" + UUID.randomUUID() + "/notifications",
                                        user),
                                channel));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                frame(StompCommand.SUBSCRIBE, "/topic/private", user), channel));
    }

    @Test
    void nonMemberCannotReadAnotherRoom() {
        UUID id = UUID.randomUUID(), room = UUID.randomUUID();
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "HS512")
                        .claim("user_id", id.toString())
                        .build();
        var user = new JwtAuthenticationToken(jwt, List.of());
        var decoder = mock(CustomJwtDecoder.class);
        when(decoder.decode("token")).thenReturn(jwt);
        var converter = mock(JwtAuthenticationConverter.class);
        when(converter.convert(jwt)).thenReturn(user);
        var db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Integer.class), eq(room), eq(id), eq(id)))
                .thenReturn(0);
        var interceptor = new AuthenticatedStompInterceptor(decoder, converter, db);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                frame(StompCommand.SUBSCRIBE, "/topic/chat/" + room, user),
                                mock(MessageChannel.class)));
    }
}
