package com.carmarket.chat.repository;

import com.carmarket.chat.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, UUID> {
}
