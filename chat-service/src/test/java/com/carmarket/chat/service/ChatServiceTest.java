package com.carmarket.chat.service;

import com.carmarket.chat.dto.SendMessageRequest;
import com.carmarket.chat.entity.Conversation;
import com.carmarket.chat.entity.Message;
import com.carmarket.chat.event.MessageSentEvent;
import com.carmarket.chat.repository.ConversationRepository;
import com.carmarket.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private ChatService chatService;

    private final UUID buyerId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();
    private Conversation convo;

    @BeforeEach
    void setUp() {
        convo = Conversation.builder()
            .id(UUID.randomUUID()).carId(carId).buyerId(buyerId).sellerId(sellerId).build();
        when(conversationRepository.findById(convo.getId())).thenReturn(Optional.of(convo));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });
    }

    @Test
    void buyerMessageNotifiesSeller() {
        chatService.saveMessage(buyerId, new SendMessageRequest(carId, sellerId, convo.getId(), "Hi"));

        MessageSentEvent event = publishedEvent();
        assertThat(event.senderId()).isEqualTo(buyerId);
        assertThat(event.recipientId()).isEqualTo(sellerId);
        assertThat(event.conversationId()).isEqualTo(convo.getId());
    }

    @Test
    void sellerReplyNotifiesBuyer() {
        chatService.saveMessage(sellerId, new SendMessageRequest(carId, sellerId, convo.getId(), "Hello"));

        MessageSentEvent event = publishedEvent();
        assertThat(event.senderId()).isEqualTo(sellerId);
        assertThat(event.recipientId()).isEqualTo(buyerId);
        assertThat(event.conversationId()).isEqualTo(convo.getId());
    }

    private MessageSentEvent publishedEvent() {
        ArgumentCaptor<MessageSentEvent> captor = ArgumentCaptor.forClass(MessageSentEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }
}
