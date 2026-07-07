package com.carmarket.chat.ws;

import com.carmarket.chat.dto.MessageResponse;
import com.carmarket.chat.dto.SendMessageRequest;
import com.carmarket.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Realtime chat. Client sends to /app/chat.send.
 * Server delivers to each participant's private queue: /user/{userId}/queue/messages
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(@Valid @Payload SendMessageRequest req, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        MessageResponse saved = chatService.saveMessage(senderId, req);

        // Deliver to both participants (sender gets confirmation, recipient gets the message)
        for (UUID participant : chatService.participantsOf(saved.conversationId())) {
            messagingTemplate.convertAndSendToUser(
                participant.toString(),
                "/queue/messages",
                saved);
        }
    }
}
