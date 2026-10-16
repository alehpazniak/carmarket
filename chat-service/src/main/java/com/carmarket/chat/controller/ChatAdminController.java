package com.carmarket.chat.controller;

import com.carmarket.chat.service.ChatStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway: /api/chat/admin/** — ADMIN only (SecurityConfig). Statistics only, never message content. */
@RestController
@RequestMapping("/chat/admin")
@RequiredArgsConstructor
public class ChatAdminController {

    private final ChatStatsService statsService;

    @GetMapping("/stats")
    public ChatStatsService.ChatStats stats() {
        return statsService.stats();
    }
}
