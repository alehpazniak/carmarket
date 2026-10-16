package com.carmarket.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatStatsService.class)
@Testcontainers
class ChatStatsServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ChatStatsService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void countsConversationsMessagesAndBlockedUsers() {
        UUID conversation = UUID.randomUUID();
        jdbc.update("INSERT INTO conversations (id, car_id, buyer_id, seller_id) VALUES (?, ?, ?, ?)",
            conversation, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("INSERT INTO conversations (id, car_id, buyer_id, seller_id, created_at) VALUES (?, ?, ?, ?, now() - interval '40 days')",
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO messages (conversation_id, sender_id, content) VALUES (?, ?, 'hej')", conversation, UUID.randomUUID());
        }
        jdbc.update("INSERT INTO blocked_users (user_id) VALUES (?)", UUID.randomUUID());

        ChatStatsService.ChatStats stats = service.stats();

        assertThat(stats.conversations()).isEqualTo(2);
        assertThat(stats.newConversationsLast30Days()).isEqualTo(1);
        assertThat(stats.messagesLast7Days()).isEqualTo(3);
        assertThat(stats.dailyMessages()).hasSize(30);
        assertThat(stats.dailyMessages().get(29).count()).isEqualTo(3);
        assertThat(stats.blockedUsers()).isEqualTo(1);
    }
}
