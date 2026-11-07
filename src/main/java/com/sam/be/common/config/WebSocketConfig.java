package com.sam.be.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@lombok.RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final AuthenticatedStompInterceptor auth;
    private final com.sam.be.common.security.config.CorsProperties cors;

    @Override
    public void configureClientInboundChannel(
            org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(auth);
    }

    @Override
    public void configureClientOutboundChannel(
            org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(
                new org.springframework.messaging.support.ChannelInterceptor() {
                    @Override
                    public org.springframework.messaging.Message<?> preSend(
                            org.springframework.messaging.Message<?> message,
                            org.springframework.messaging.MessageChannel channel) {
                        return auth.authorizeOutbound(message);
                    }
                });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Mở endpoint /ws cho Web/App kết nối tới. Cho phép gọi chéo domain (CORS)
        registry.addEndpoint("/ws")
                .setAllowedOrigins(cors.getAllowedOrigins().toArray(String[]::new))
                .withSockJS(); // Hỗ trợ fallback cho các trình duyệt cũ
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix cho các kênh mà server sẽ đẩy dữ liệu về (Broker)
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix cho các request từ client gửi lên server (nếu client muốn chat lại)
        registry.setApplicationDestinationPrefixes("/app");
    }
}
