package com.carmarket.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Map;

/**
 * On STOMP CONNECT, promote the userId (stored at handshake) to the session Principal,
 * so @MessageMapping methods receive it and /user/{id}/... destinations resolve.
 */
@Configuration
public class StompPrincipalConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs != null && attrs.get("userId") != null) {
                        String userId = attrs.get("userId").toString();
                        accessor.setUser((Principal) () -> userId);
                    }
                }
                return message;
            }
        });
    }
}
