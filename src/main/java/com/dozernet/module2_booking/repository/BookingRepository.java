package com.dozernet.module2_booking.repository;

import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module3_fleet.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            join fetch b.machine
            join fetch b.customer
            where b.customer = :customer
            order by b.startDate desc
            """)
    List<Booking> findByCustomerOrderByStartDateDesc(@Param("customer") User customer);

    @Query("""
            select b from Booking b
            join fetch b.machine
            join fetch b.customer
            where b.status = :status
            order by b.createdAt desc
            """)
    List<Booking> findByStatusOrderByCreatedAtDesc(@Param("status") BookingStatus status);

    @Query("""
            select b from Booking b
            join fetch b.machine
            join fetch b.customer
            order by b.createdAt desc
            """)
    List<Booking> findAllByOrderByCreatedAtDesc();

    @Query("""
            select b from Booking b
            join fetch b.machine
            join fetch b.customer
            where b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    long countByStatus(BookingStatus status);

    boolean existsByMachine(Machine machine);

    /** Bookings overlapping a date range, whatever the machine - used by reports. */
    @Query("""
            select b from Booking b
            join fetch b.machine
            where b.status in (com.dozernet.module2_booking.entity.BookingStatus.APPROVED,
                               com.dozernet.module2_booking.entity.BookingStatus.COMPLETED)
              and b.startDate <= :end
              and b.endDate   >= :start
            """)
    List<Booking> findActiveBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Overlapping bookings for a machine that still occupy the calendar
     * (PENDING or APPROVED). Two ranges overlap when start <= otherEnd AND
     * end >= otherStart. Used to prevent double-booking.
     */
    @Query("""
            select b from Booking b
            where b.machine = :machine
              and b.status in (com.dozernet.module2_booking.entity.BookingStatus.PENDING,
                               com.dozernet.module2_booking.entity.BookingStatus.APPROVED)
              and b.startDate <= :end
              and b.endDate   >= :start
            """)
    List<Booking> findOverlapping(@Param("machine") Machine machine,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    /**
     * Approved bookings with a paid invoice whose rental window includes {@code date}.
     * Unpaid approvals are not treated as machines on site.
     */
    @Query("""
            select b from Booking b
            join fetch b.machine
            join fetch b.customer
            where b.status = com.dozernet.module2_booking.entity.BookingStatus.APPROVED
              and b.startDate <= :date
              and b.endDate   >= :date
              and exists (
                  select i from Invoice i
                  where i.booking = b
                    and i.status = com.dozernet.module6_payment.entity.InvoiceStatus.PAID
              )
            order by b.jobSiteDistrict asc, b.machine.model asc
            """)
    List<Booking> findActiveDeploymentsOn(@Param("date") LocalDate date);
}
