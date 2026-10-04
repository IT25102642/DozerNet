package com.dozernet.module2_booking.repository;

import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.MachineReplacementLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository (DAO) pattern via Spring Data JPA - machine replacement history.
 */
public interface MachineReplacementLogRepository extends JpaRepository<MachineReplacementLog, Long> {

    List<MachineReplacementLog> findByBookingOrderByCreatedAtDesc(Booking booking);

    List<MachineReplacementLog> findAllByOrderByCreatedAtDesc();
}
