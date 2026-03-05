package com.example.paymentservice.repository;

import com.example.paymentservice.constants.OutboxStatus;
import com.example.paymentservice.entity.OutboxMessage;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxMessage> findAllByStatus(OutboxStatus status);
}
