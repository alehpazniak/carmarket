package com.carmarket.chat.service;

import com.carmarket.chat.dto.MessageResponse;
import com.carmarket.chat.dto.SendMessageRequest;
import com.carmarket.chat.entity.Conversation;
import com.carmarket.chat.entity.Message;
import com.carmarket.chat.repository.ConversationRepository;
import com.carmarket.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Persists a message. Finds-or-creates the (carId, buyerId) conversation.
     * The sender is always the authenticated user. If the sender is the seller,
     * the buyer must be resolved from an existing conversation.
     */
    @Transactional
    public MessageResponse saveMessage(UUID senderId, SendMessageRequest req) {
        Conversation convo = conversationRepository.findByCarIdAndBuyerId(req.carId(), resolveBuyer(senderId, req))
            .orElseGet(() -> conversationRepository.save(Conversation.builder()
                .carId(req.carId())
                .buyerId(resolveBuyer(senderId, req))
                .sellerId(req.sellerId())
                .build()));

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

        return MessageResponse.from(saved);
    }

    /**
     * If the sender is the seller of the listing, the buyer is the "other side".
     * For a first message, the sender is always the buyer (seller can't start a thread
     * with a buyer who hasn't reached out — there's no buyerId to target).
     */
    private UUID resolveBuyer(UUID senderId, SendMessageRequest req) {
        // If sender is the seller, this must be a reply → buyer comes from existing convo.
        // We look it up; if not found and sender==seller, it's an error (no thread to reply to).
        if (senderId.equals(req.sellerId())) {
            return conversationRepository.findByCarIdAndBuyerId(req.carId(), req.sellerId())
                .map(Conversation::getBuyerId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Seller cannot initiate a conversation; no existing thread"));
        }
        // Sender is the buyer
        return senderId;
    }

    @Transactional(readOnly = true)
    public List<Conversation> getMyConversations(UUID userId) {
        return conversationRepository.findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(userId, userId);
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
