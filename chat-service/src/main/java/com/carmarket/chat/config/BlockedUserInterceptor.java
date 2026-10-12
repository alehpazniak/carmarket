package com.carmarket.chat.config;

import com.carmarket.chat.service.BlockedUserService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.util.Map;

/**
 * Refuses CONNECT and SEND from a blocked account. The handshake already rejects users who are
 * blocked when they connect; this covers sockets opened before the block.
 */
public class BlockedUserInterceptor implements ChannelInterceptor {

    private final BlockedUserService blockedUsers;

    public BlockedUserInterceptor(BlockedUserService blockedUsers) {
        this.blockedUsers = blockedUsers;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SEND && command != StompCommand.CONNECT) return message;
        if (blockedUsers.isBlocked(userId(accessor))) {
            throw new MessageDeliveryException(message, "Account blocked");
        }
        return message;
    }

    private static String userId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user != null) return user.getName();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        Object id = attrs != null ? attrs.get("userId") : null;
        return id != null ? id.toString() : null;
    }
}
