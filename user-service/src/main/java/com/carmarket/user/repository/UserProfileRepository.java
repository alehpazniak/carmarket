package com.carmarket.user.repository;

import com.carmarket.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmail(String email);

    List<UserProfile> findByRoleAndActive(UserProfile.UserRole role, boolean active);
}
