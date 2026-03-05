package com.example.event.repository;

import com.example.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    @Query(value = "SELECT e FROM Event e JOIN FETCH e.venue JOIN FETCH e.category LEFT JOIN FETCH e.artists",
           countQuery = "SELECT COUNT(e) FROM Event e")
    Page<Event> findAllWithVenueAndCategory(Pageable pageable);
}
