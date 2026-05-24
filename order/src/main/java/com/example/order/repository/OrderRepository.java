package com.example.order.repository;

import com.example.order.constants.OrderStatus;
import com.example.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // Used by the inventory orphan reconciler. Returns the subset of holdIds that
    // still belong to an order in a state where the seats are legitimately held.
    @Query("SELECT o.holdId FROM Order o WHERE o.holdId IN :holdIds AND o.status IN :statuses")
    List<String> findActiveHoldIds(@Param("holdIds") Collection<String> holdIds,
                                   @Param("statuses") Collection<OrderStatus> statuses);

    // HTTP idempotency lookup for POST /api/orders. The (userId, idempotencyKey)
    // unique constraint guarantees at most one match.
    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}

