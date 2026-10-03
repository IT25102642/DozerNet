package com.dozernet.module6_payment.repository;

import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("""
            select i from Invoice i
            join fetch i.customer
            join fetch i.booking b
            join fetch b.machine
            where i.customer = :customer
            order by i.issuedDate desc
            """)
    List<Invoice> findByCustomerOrderByIssuedDateDesc(@Param("customer") User customer);

    @Query("""
            select i from Invoice i
            join fetch i.customer
            join fetch i.booking b
            join fetch b.machine
            order by i.issuedDate desc
            """)
    List<Invoice> findAllByOrderByIssuedDateDesc();

    @Query("""
            select i from Invoice i
            join fetch i.customer
            join fetch i.booking b
            join fetch b.machine
            where i.id = :id
            """)
    Optional<Invoice> findByIdWithDetails(@Param("id") Long id);

    boolean existsByBooking(Booking booking);

    Optional<Invoice> findByBooking(Booking booking);

    long countByStatus(InvoiceStatus status);

    long countByCustomerAndStatusNot(User customer, InvoiceStatus status);

    List<Invoice> findByStatusOrderByIssuedDateAsc(InvoiceStatus status);

    /** Highest sequence number issued in a given year, for INV-YYYY-NNNNN numbering. */
    @Query("""
            select count(i) from Invoice i
            where i.invoiceNumber like concat('INV-', :year, '-%')
            """)
    long countIssuedInYear(@Param("year") String year);

    /** Invoices in a date window - the basis of the monthly revenue report. */
    @Query("""
            select i from Invoice i
            join fetch i.customer
            join fetch i.booking b
            join fetch b.machine
            where i.issuedDate between :from and :to
            order by i.issuedDate asc
            """)
    List<Invoice> findIssuedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Everything still owing, oldest first - the outstanding payments report. */
    @Query("""
            select i from Invoice i
            join fetch i.customer
            join fetch i.booking b
            join fetch b.machine
            where i.status in (com.dozernet.module6_payment.entity.InvoiceStatus.UNPAID,
                               com.dozernet.module6_payment.entity.InvoiceStatus.PARTIALLY_PAID)
            order by i.issuedDate asc
            """)
    List<Invoice> findOutstanding();
}
