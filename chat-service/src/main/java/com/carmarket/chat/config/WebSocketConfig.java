package com.carmarket.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // ─────────────────────────────────────────────────────────────
        // IN-MEMORY broker (single instance). To scale to multiple instances,
        // replace enableSimpleBroker(...) with a relay to Redis/RabbitMQ:
        //
        //   config.enableStompBrokerRelay("/topic", "/queue")
        //         .setRelayHost("rabbitmq").setRelayPort(61613)
        //         .setClientLogin("guest").setClientPasscode("guest");
        //
        // (Redis has no native STOMP relay; RabbitMQ/ActiveMQ do. For Redis
        //  you'd use a pub/sub bridge. See note in the message.)
        // ─────────────────────────────────────────────────────────────
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*"); // TODO: restrict to your frontend origin in prod
        // SockJS fallback (optional) — enable if frontend needs it:
        // .withSockJS();
    }
}
