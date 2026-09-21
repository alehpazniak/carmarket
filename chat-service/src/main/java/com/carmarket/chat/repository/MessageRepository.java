package com.carmarket.chat.repository;

import com.carmarket.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID senderId);

    @Query("select count(m) from Message m, Conversation c where m.conversationId = c.id "
        + "and (c.buyerId = :userId or c.sellerId = :userId) "
        + "and m.senderId <> :userId and m.readAt is null")
    long countUnreadForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("update Message m set m.readAt = :now where m.conversationId = :conversationId "
        + "and m.senderId <> :userId and m.readAt is null")
    int markRead(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId,
                 @Param("now") Instant now);
}
