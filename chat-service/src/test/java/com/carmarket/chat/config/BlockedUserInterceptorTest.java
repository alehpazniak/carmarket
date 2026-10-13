package com.carmarket.chat.config;

import com.carmarket.chat.service.BlockedUserService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockedUserInterceptorTest {

    private final BlockedUserService blockedUsers = mock(BlockedUserService.class);
    private final BlockedUserInterceptor interceptor = new BlockedUserInterceptor(blockedUsers);
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> frame(StompCommand command, String userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        Principal principal = () -> userId;
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void aBlockedUserCantSendOnAnOpenSocket() {
        when(blockedUsers.isBlocked("u-1")).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "u-1"), channel))
            .isInstanceOf(MessageDeliveryException.class).hasMessageContaining("Account blocked");
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "u-1"), channel))
            .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void otherUsersAndOtherFramesPass() {
        when(blockedUsers.isBlocked("u-2")).thenReturn(false);
        when(blockedUsers.isBlocked("u-1")).thenReturn(true);

        assertThat(interceptor.preSend(frame(StompCommand.SEND, "u-2"), channel)).isNotNull();
        assertThat(interceptor.preSend(frame(StompCommand.DISCONNECT, "u-1"), channel)).isNotNull();
    }
}
