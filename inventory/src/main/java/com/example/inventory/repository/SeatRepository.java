package com.example.inventory.repository;

import com.example.inventory.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findSeatsByEventId(int eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT s FROM Seat s
                WHERE s.eventId = :eventId
                  AND s.section = :section
                  AND s.row = :row
                  AND s.number IN :seatNumbers
            """)
    List<Seat> findSeatsForUpdate(
            @Param("eventId") int eventId,
            @Param("section") int section,
            @Param("row") String row,
            @Param("seatNumbers") List<Integer> seatNumbers
    );
}
