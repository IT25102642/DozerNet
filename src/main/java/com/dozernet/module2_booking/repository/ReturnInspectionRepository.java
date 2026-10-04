package com.dozernet.module2_booking.repository;

import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.ReturnInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repository (DAO) pattern via Spring Data JPA - return inspection reports.
 *
 * <p>The list queries fetch the booking's machine and customer up front: the
 * view renders them after the transaction closes
 * ({@code spring.jpa.open-in-view=false}), so lazy proxies would fail.</p>
 */
public interface ReturnInspectionRepository extends JpaRepository<ReturnInspection, Long> {

    Optional<ReturnInspection> findByBooking(Booking booking);

    boolean existsByBooking(Booking booking);

    @Query("""
            select r from ReturnInspection r
            join fetch r.booking b
            join fetch b.machine
            join fetch b.customer
            where r.damageReported = true
            order by r.createdAt desc
            """)
    List<ReturnInspection> findByDamageReportedTrueOrderByCreatedAtDesc();

    @Query("""
            select r from ReturnInspection r
            join fetch r.booking b
            join fetch b.machine
            join fetch b.customer
            order by r.createdAt desc
            """)
    List<ReturnInspection> findAllByOrderByCreatedAtDesc();
}
