package com.carmarket.notification.repository;

import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByTypeAndRecipientIdAndDedupKeyAndStatusIn(
        NotificationType type, UUID recipientId, String dedupKey, Collection<NotificationStatus> statuses);

    /** SKIP LOCKED lets several instances share the queue without sending duplicates. */
    @Query(value = """
        SELECT * FROM notifications
        WHERE status = 'PENDING' AND send_at <= :now
        ORDER BY send_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Notification> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("""
        UPDATE Notification n SET n.status = :to
        WHERE n.type = :type AND n.recipientId = :recipientId AND n.dedupKey = :dedupKey AND n.status = :from
        """)
    int transition(@Param("type") NotificationType type, @Param("recipientId") UUID recipientId,
                   @Param("dedupKey") String dedupKey,
                   @Param("from") NotificationStatus from, @Param("to") NotificationStatus to);
}
