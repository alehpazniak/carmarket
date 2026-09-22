package com.carmarket.notification.service;

import com.carmarket.notification.config.NotificationProperties;
import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.entity.Recipient;
import com.carmarket.notification.mail.EmailContent;
import com.carmarket.notification.mail.EmailContentFactory;
import com.carmarket.notification.mail.EmailSender;
import com.carmarket.notification.repository.NotificationRepository;
import com.carmarket.notification.repository.RecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    NotificationRepository notifications;
    @Mock
    RecipientRepository recipients;
    @Mock
    EmailSender emailSender;

    NotificationDispatcher dispatcher;

    private final UUID sellerId = UUID.randomUUID();
    private Notification notification;

    @BeforeEach
    void setUp() {
        var props = new NotificationProperties("no-reply@test", Duration.ofMinutes(3), 50, 3);
        dispatcher = new NotificationDispatcher(notifications, recipients, new EmailContentFactory(), emailSender, props);
        notification = Notification.builder()
            .type(NotificationType.NEW_CHAT_MESSAGE)
            .recipientId(sellerId)
            .dedupKey("conv-1")
            .sendAt(Instant.now().minusSeconds(1))
            .build();
        when(notifications.lockDue(any(), anyInt())).thenReturn(List.of(notification));
    }

    @Test
    void sendsNewMessageEmailToSeller() {
        when(recipients.findById(sellerId)).thenReturn(Optional.of(
            Recipient.builder().userId(sellerId).email("seller@example.com").displayName("Sam").build()));

        dispatcher.dispatchDue();

        var content = org.mockito.ArgumentCaptor.forClass(EmailContent.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("seller@example.com"), content.capture());
        assertThat(content.getValue().subject()).isEqualTo("You got a new message");
        assertThat(content.getValue().body()).contains("You got new message");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    void failsWhenRecipientEmailUnknown() {
        when(recipients.findById(sellerId)).thenReturn(Optional.empty());

        dispatcher.dispatchDue();

        verify(emailSender, never()).send(any(), any());
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void smtpFailureIsRetriedLaterThenGivenUp() {
        when(recipients.findById(sellerId)).thenReturn(Optional.of(
            Recipient.builder().userId(sellerId).email("seller@example.com").build()));
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(any(), any());

        dispatcher.dispatchDue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getSendAt()).isAfter(Instant.now());

        dispatcher.dispatchDue();
        dispatcher.dispatchDue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastError()).isEqualTo("smtp down");
    }
}
