package com.carmarket.user.kafka;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserEventConsumerTest {

    private final UserProfileRepository repository = mock(UserProfileRepository.class);
    private final UserEventConsumer consumer = new UserEventConsumer(repository);

    @Test
    void blockAndUnblockToggleTheProfile() {
        UserProfile profile = UserProfile.builder().id(UUID.randomUUID()).email("a@b.pl").role(UserProfile.UserRole.USER).build();
        when(repository.findById(profile.getId())).thenReturn(Optional.of(profile));
        Map<String, String> event = Map.of("userId", profile.getId().toString(), "reason", "spam");

        consumer.handleBlocked(event);
        assertThat(profile.isActive()).isFalse();

        consumer.handleUnblocked(event);
        assertThat(profile.isActive()).isTrue();
    }

    @Test
    void unknownUsersAndMalformedEventsAreIgnored() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        consumer.handleBlocked(Map.of("userId", UUID.randomUUID().toString()));
        consumer.handleBlocked(Map.of("reason", "x"));

        verify(repository, never()).save(any());
    }
}
