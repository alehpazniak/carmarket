package com.carmarket.chat.service;

import com.carmarket.chat.entity.BlockedUser;
import com.carmarket.chat.repository.BlockedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedUserService {

    private final BlockedUserRepository repository;

    public boolean isBlocked(String userId) {
        try {
            return userId != null && repository.existsById(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional
    public void block(UUID userId, String reason) {
        repository.save(new BlockedUser(userId, reason, Instant.now()));
        log.info("Chat disabled for blocked user {}", userId);
    }

    @Transactional
    public void unblock(UUID userId) {
        repository.deleteById(userId);
        log.info("Chat enabled again for user {}", userId);
    }
}
