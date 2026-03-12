package com.example.order.repository;

import com.example.order.constants.OutboxMessageStatus;
import com.example.order.entity.EmailOutboxMessage;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, Long> {

    @Query(
            nativeQuery = true,
            value = "select * from email_outbox e where e.status = 'PENDING' order by created_at limit :batchSize for update skip locked"
    )
    List<EmailOutboxMessage> findPendingBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE EmailOutboxMessage m SET m.status = com.example.order.constants.OutboxMessageStatus.PENDING WHERE m.status = com.example.order.constants.OutboxMessageStatus.PROCESSING AND m.updatedAt < :cutoff")
    void resetStuckProcessing(@Param("cutoff") LocalDateTime cutoff);
}
