package com.carmarket.chat.service;

import com.carmarket.chat.dto.ConversationResponse;
import com.carmarket.chat.dto.MessageResponse;
import com.carmarket.chat.dto.SendMessageRequest;
import com.carmarket.chat.entity.Conversation;
import com.carmarket.chat.event.ConversationReadEvent;
import com.carmarket.chat.event.MessageSentEvent;
import com.carmarket.chat.entity.Message;
import com.carmarket.chat.repository.ConversationRepository;
import com.carmarket.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    /**
     * Persists a message. Finds-or-creates the (carId, buyerId) conversation.
     * The sender is always the authenticated user. If the sender is the seller,
     * the buyer must be resolved from an existing conversation.
     */
    @Transactional
    public MessageResponse saveMessage(UUID senderId, SendMessageRequest req) {
        Conversation convo = findOrCreateConversation(senderId, req);

        // Authorization: sender must be a participant
        if (!senderId.equals(convo.getBuyerId()) && !senderId.equals(convo.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }

        Message saved = messageRepository.save(Message.builder()
            .conversationId(convo.getId())
            .senderId(senderId)
            .content(req.content())
            .build());

        convo.setLastMessageAt(Instant.now());
        conversationRepository.save(convo);

        // Only buyer -> seller messages trigger the "you got a new message" email.
        if (senderId.equals(convo.getBuyerId())) {
            events.publishEvent(new MessageSentEvent(saved.getId(), convo.getId(), convo.getCarId(),
                convo.getBuyerId(), convo.getSellerId(), saved.getCreatedAt()));
        }

        return MessageResponse.from(saved);
    }

    /**
     * With a conversationId (reply in an existing thread) the thread is loaded directly.
     * Otherwise the sender must be the buyer: find-or-create the (carId, buyerId) thread.
     * A seller can't start a thread since there's no buyer to target.
     */
    private Conversation findOrCreateConversation(UUID senderId, SendMessageRequest req) {
        if (req.conversationId() != null) {
            return conversationRepository.findById(req.conversationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        }
        if (senderId.equals(req.sellerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Seller cannot initiate a conversation; no existing thread");
        }
        return conversationRepository.findByCarIdAndBuyerId(req.carId(), senderId)
            .orElseGet(() -> conversationRepository.save(Conversation.builder()
                .carId(req.carId())
                .buyerId(senderId)
                .sellerId(req.sellerId())
                .build()));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        return conversationRepository.findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(userId, userId)
            .stream()
            .map(c -> ConversationResponse.from(c,
                messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), userId)))
            .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    /** Marks all messages sent by the other participant as read. */
    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        Conversation convo = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!userId.equals(convo.getBuyerId()) && !userId.equals(convo.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant");
        }
        Instant readAt = Instant.now();
        messageRepository.markRead(conversationId, userId, readAt);
        events.publishEvent(new ConversationReadEvent(conversationId, userId, readAt));
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID userId, UUID conversationId, Pageable pageable) {
        Conversation convo = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!userId.equals(convo.getBuyerId()) && !userId.equals(convo.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant");
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable)
            .map(MessageResponse::from);
    }

    /**
     * The two participant ids of a conversation — used to route the realtime message.
     */
    @Transactional(readOnly = true)
    public List<UUID> participantsOf(UUID conversationId) {
        Conversation c = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        return List.of(c.getBuyerId(), c.getSellerId());
    }
}
