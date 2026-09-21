package com.carmarket.chat.controller;

import com.carmarket.chat.dto.ConversationResponse;
import com.carmarket.chat.dto.MessageResponse;
import com.carmarket.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Chat history over REST (goes through the gateway → X-User-Id is trusted here).
 * Realtime send/receive is over WebSocket, not REST.
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> myConversations(
        @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(chatService.getMyConversations(UUID.fromString(userId)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(chatService.unreadCount(UUID.fromString(userId)));
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(
        @PathVariable UUID id, @RequestHeader("X-User-Id") String userId) {
        chatService.markRead(UUID.fromString(userId), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> messages(
        @PathVariable UUID id,
        @RequestHeader("X-User-Id") String userId,
        @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
            chatService.getMessages(UUID.fromString(userId), id, pageable));
    }
}
