package com.example.event.repository;

import com.example.event.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    @Query(
            nativeQuery = true,
            value = "SELECT * FROM event_outbox WHERE status = 'PENDING' ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED"
    )
    List<OutboxMessage> findPendingBatch();

    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = com.example.event.constants.OutboxStatus.PENDING WHERE m.status = com.example.event.constants.OutboxStatus.PROCESSING AND m.updatedAt < :cutoff")
    void resetStuckProcessing(@Param("cutoff") LocalDateTime cutoff);
}
