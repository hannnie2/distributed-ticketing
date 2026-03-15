package com.example.order.repository;

import com.example.order.constants.OrderStatus;
import com.example.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.paymentIntentId = :pid " +
           "WHERE o.id = :id AND o.status = :status AND o.paymentIntentId IS NULL")
    int savePaymentResult(@Param("id") Long id, @Param("pid") String pid, @Param("status") OrderStatus status);
}

