package com.carmarket.chat.service;

import com.carmarket.chat.dto.MessageResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
        lenient().when(conversationRepository.findById(convo.getId())).thenReturn(Optional.of(convo));
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });
    }

    @Test
    void buyerMessageNotifiesSeller() {
        MessageResponse saved = chatService.saveMessage(buyerId, new SendMessageRequest(carId, sellerId, convo.getId(), "Hi"));

        assertThat(saved.carId()).isEqualTo(carId);
        assertThat(saved.conversationId()).isEqualTo(convo.getId());

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

    @Test
    void firstBuyerMessageCreatesConversationAndCarriesCarId() {
        when(conversationRepository.findByCarIdAndBuyerId(carId, buyerId)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        MessageResponse saved = chatService.saveMessage(buyerId, new SendMessageRequest(carId, sellerId, null, "Hi"));

        assertThat(saved.carId()).isEqualTo(carId);
        assertThat(saved.conversationId()).isNotNull().isNotEqualTo(convo.getId());
    }

    @Test
    void historyMessagesCarryConversationCarId() {
        Message m = Message.builder().id(UUID.randomUUID()).conversationId(convo.getId())
            .senderId(sellerId).content("Hello").createdAt(Instant.now()).build();
        PageRequest pageable = PageRequest.of(0, 50);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(convo.getId(), pageable))
            .thenReturn(new PageImpl<>(List.of(m), pageable, 1));

        Page<MessageResponse> page = chatService.getMessages(buyerId, convo.getId(), pageable);

        assertThat(page.getContent()).singleElement()
            .satisfies(r -> assertThat(r.carId()).isEqualTo(carId));
    }

    private MessageSentEvent publishedEvent() {
        ArgumentCaptor<MessageSentEvent> captor = ArgumentCaptor.forClass(MessageSentEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }
}
