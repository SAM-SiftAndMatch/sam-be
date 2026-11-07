package com.sam.be.common.config;

import com.sam.be.common.security.config.JwtAuthenticationConverter;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedStompInterceptor implements ExecutorChannelInterceptor {
    private final CustomJwtDecoder decoder;
    private final JwtAuthenticationConverter converter;
    private final JdbcTemplate db;
    private final java.util.concurrent.ConcurrentMap<String, String> sessionTokens =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        if (headers.getCommand() == StompCommand.CONNECT) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw denied();
            headers.setUser(converter.convert(decoder.decode(authorization.substring(7))));
            if (headers.getSessionId() != null)
                sessionTokens.put(headers.getSessionId(), authorization.substring(7));
            return message;
        }
        if (headers.getCommand() == StompCommand.DISCONNECT) {
            if (headers.getSessionId() != null) sessionTokens.remove(headers.getSessionId());
            return message;
        }
        if (!(headers.getUser() instanceof JwtAuthenticationToken token)) throw denied();
        var auth = converter.convert(decoder.decode(token.getToken().getTokenValue()));
        headers.setUser(auth);
        UUID user = UUID.fromString(token.getToken().getClaimAsString("user_id"));
        if (headers.getCommand() == StompCommand.SUBSCRIBE
                || headers.getCommand() == StompCommand.SEND) {
            String destination = headers.getDestination();
            if (destination == null) throw denied();
            String[] parts = destination.split("/");
            try {
                if (headers.getCommand() == StompCommand.SUBSCRIBE
                        && parts.length == 5
                        && parts[1].equals("topic")
                        && parts[2].equals("users")
                        && parts[4].equals("notifications")) {
                    if (!user.equals(UUID.fromString(parts[3]))) throw denied();
                    return message;
                }
                boolean subscribing = headers.getCommand() == StompCommand.SUBSCRIBE;
                if (subscribing && (parts.length != 4 || !parts[1].equals("topic"))) throw denied();
                if (!subscribing && (parts.length != 5 || !parts[1].equals("app"))) throw denied();
                UUID subject = UUID.fromString(parts[3]);
                String table;
                if (parts[2].equals("chat")) {
                    table = "chat_rooms";
                    if (!subscribing && !parts[4].equals("send")) throw denied();
                } else if (parts[2].equals("contracts")) {
                    table = "contracts";
                    if (!subscribing
                            && !java.util.Set.of("sync", "sign", "confirm").contains(parts[4]))
                        throw denied();
                } else throw denied();
                // table is selected exclusively from the two literals above, never from user input.
                Integer count =
                        db.queryForObject(
                                "SELECT COUNT(*) FROM "
                                        + table
                                        + " WHERE id=? AND (client_id=? OR freelancer_id=?)",
                                Integer.class,
                                subject,
                                user,
                                user);
                if (count == null || count != 1) throw denied();
                if (message.getPayload() instanceof byte[] body && body.length > 262144)
                    throw denied();
            } catch (IllegalArgumentException ex) {
                throw denied();
            }
        }
        return message;
    }

    public Message<?> authorizeOutbound(Message<?> message) {
        if (org.springframework.messaging.simp.SimpMessageHeaderAccessor.getMessageType(
                        message.getHeaders())
                != org.springframework.messaging.simp.SimpMessageType.MESSAGE) return message;
        String session =
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.getSessionId(
                        message.getHeaders());
        String token = session == null ? null : sessionTokens.get(session);
        if (token == null) return null;
        try {
            converter.convert(decoder.decode(token));
            return message;
        } catch (Exception ex) {
            sessionTokens.remove(session);
            return null;
        }
    }

    @org.springframework.context.event.EventListener
    public void disconnected(
            org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        sessionTokens.remove(event.getSessionId());
    }

    @Override
    public Message<?> beforeHandle(
            Message<?> message, MessageChannel channel, MessageHandler handler) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers != null && headers.getUser() instanceof JwtAuthenticationToken auth)
            SecurityContextHolder.getContext().setAuthentication(auth);
        return message;
    }

    @Override
    public void afterMessageHandled(
            Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        SecurityContextHolder.clearContext();
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("WebSocket authorization required");
    }
}
