package com.carmarket.chat.repository;

import com.carmarket.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByCarIdAndBuyerId(UUID carId, UUID buyerId);

    /** All conversations where the user is either buyer or seller, newest activity first. */
    List<Conversation> findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(UUID buyerId, UUID sellerId);
}
