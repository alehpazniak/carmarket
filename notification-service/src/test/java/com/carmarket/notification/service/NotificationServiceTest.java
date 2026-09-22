package com.carmarket.notification.service;

import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    NotificationService service;

    private final UUID seller = UUID.randomUUID();

    @Test
    void schedulesEmailAfterTheDelay() {
        when(repository.existsByTypeAndRecipientIdAndDedupKeyAndStatusIn(any(), any(), any(), any())).thenReturn(false);

        Instant before = Instant.now();
        boolean queued = service.schedule(NotificationType.NEW_CHAT_MESSAGE, seller, "conv-1",
            Duration.ofMinutes(3), Map.of("conversationId", "conv-1"));

        assertThat(queued).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getValue().getSendAt()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(3)));
    }

    @Test
    void secondMessageInSameConversationDoesNotQueueAnotherEmail() {
        when(repository.existsByTypeAndRecipientIdAndDedupKeyAndStatusIn(any(), any(), any(), any())).thenReturn(true);

        boolean queued = service.schedule(NotificationType.NEW_CHAT_MESSAGE, seller, "conv-1",
            Duration.ofMinutes(3), Map.of());

        assertThat(queued).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void readingCancelsPendingAndReArmsSent() {
        service.resolve(NotificationType.NEW_CHAT_MESSAGE, seller, "conv-1");

        verify(repository).transition(NotificationType.NEW_CHAT_MESSAGE, seller, "conv-1",
            NotificationStatus.PENDING, NotificationStatus.CANCELLED);
        verify(repository).transition(NotificationType.NEW_CHAT_MESSAGE, seller, "conv-1",
            NotificationStatus.SENT, NotificationStatus.ACKNOWLEDGED);
    }
}
