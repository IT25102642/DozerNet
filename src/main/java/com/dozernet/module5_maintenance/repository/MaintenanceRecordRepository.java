package com.dozernet.module5_maintenance.repository;

import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module5_maintenance.entity.MaintenanceRecord;
import com.dozernet.module5_maintenance.entity.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    @Query("""
            select r from MaintenanceRecord r
            join fetch r.machine
            where r.machine = :machine
            order by r.scheduledDate desc
            """)
    List<MaintenanceRecord> findByMachineOrderByScheduledDateDesc(@Param("machine") Machine machine);

    @Query("""
            select r from MaintenanceRecord r
            join fetch r.machine
            where r.status = :status
            order by r.scheduledDate asc
            """)
    List<MaintenanceRecord> findByStatusOrderByScheduledDateAsc(@Param("status") MaintenanceStatus status);

    @Query("""
            select r from MaintenanceRecord r
            join fetch r.machine
            order by r.scheduledDate desc
            """)
    List<MaintenanceRecord> findAllByOrderByScheduledDateDesc();

    long countByStatus(MaintenanceStatus status);
}
