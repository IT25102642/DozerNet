package com.dozernet.module5_maintenance.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module5_maintenance.dto.ScheduleForm;
import com.dozernet.module5_maintenance.entity.MaintenanceRecord;
import com.dozernet.module5_maintenance.entity.MaintenanceStatus;
import com.dozernet.module5_maintenance.repository.MaintenanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Maintenance &amp; Service Management. Scheduling maintenance pulls a machine out
 * of the booking pool (status MAINTENANCE); completing it records parts/cost
 * and can return the machine to service.
 *
 * <p>Beyond ad-hoc scheduling, the service flags machines that are overdue on
 * the {@value #SERVICE_INTERVAL_MONTHS}-month service interval so nothing
 * quietly slips through - see {@link #dueForService()}.</p>
 */
@Service
public class MaintenanceService {

    /** Client rule: every machine is serviced at least once a quarter. */
    public static final int SERVICE_INTERVAL_MONTHS = 3;

    private final MaintenanceRecordRepository repository;
    private final FleetService fleetService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public MaintenanceService(MaintenanceRecordRepository repository,
                              FleetService fleetService,
                              NotificationService notificationService,
                              UserRepository userRepository,
                              AuditService auditService) {
        this.repository = repository;
        this.fleetService = fleetService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MaintenanceRecord schedule(ScheduleForm form) {
        Machine machine = fleetService.getById(form.getMachineId());
        MaintenanceRecord record = new MaintenanceRecord();
        record.setMachine(machine);
        record.setScheduledDate(form.getScheduledDate());
        record.setDescription(form.getDescription().trim());
        record.setStatus(MaintenanceStatus.SCHEDULED);
        MaintenanceRecord saved = repository.save(record);

        // Keep an unsafe/unserviced machine out of the booking pool.
        fleetService.setStatus(machine.getId(), MachineStatus.MAINTENANCE);

        auditService.record("MAINTENANCE_SCHEDULED", "Machine", machine.getId(),
                machine.getModel() + " scheduled for " + form.getScheduledDate()
                        + " - " + record.getDescription());
        alert(machine, "Maintenance scheduled",
                machine.getModel() + " (" + machine.getRegistrationNumber()
                        + ") is scheduled for maintenance on " + form.getScheduledDate()
                        + " and is unavailable for booking until it returns to service. Work: "
                        + record.getDescription());
        return saved;
    }

    @Transactional
    public void complete(Long recordId, String partsUsed, BigDecimal cost, boolean returnToService) {
        MaintenanceRecord record = getById(recordId);
        record.setStatus(MaintenanceStatus.COMPLETED);
        record.setCompletedDate(LocalDate.now());
        record.setPartsUsed(partsUsed);
        record.setCost(cost);
        repository.save(record);

        Machine machine = record.getMachine();
        if (returnToService) {
            fleetService.setStatus(machine.getId(), MachineStatus.AVAILABLE);
        }

        auditService.record("MAINTENANCE_COMPLETED", "Machine", machine.getId(),
                machine.getModel() + " serviced"
                        + (cost == null ? "" : " at a cost of Rs. " + cost)
                        + (returnToService ? " - returned to service" : " - still out of service"));
        alert(machine, "Maintenance completed",
                machine.getModel() + " (" + machine.getRegistrationNumber() + ") has been serviced"
                        + (cost == null ? "" : " at a cost of Rs. " + cost) + ". "
                        + (returnToService
                        ? "It is back in the booking pool."
                        : "It remains out of service until an admin returns it."));
    }

    // ---------- Service-interval reminders ----------

    /**
     * A machine that has gone past its service interval, with how long it has
     * been since it was last seen by a mechanic.
     *
     * @param lastServiced null when the machine has never been serviced
     */
    public record ServiceDue(Machine machine, LocalDate lastServiced, long monthsSince) {

        public boolean neverServiced() {
            return lastServiced == null;
        }

        public String reason() {
            return neverServiced()
                    ? "No service recorded yet"
                    : "Last serviced " + lastServiced + " (" + monthsSince + " months ago)";
        }
    }

    /**
     * Machines that are due a service: nothing completed in the last
     * {@value #SERVICE_INTERVAL_MONTHS} months, and no service already booked in.
     * Machines already sitting in MAINTENANCE are excluded - they are in the
     * workshop already.
     */
    public List<ServiceDue> dueForService() {
        LocalDate cutOff = LocalDate.now().minusMonths(SERVICE_INTERVAL_MONTHS);
        List<ServiceDue> due = new ArrayList<>();

        for (Machine machine : fleetService.findAll()) {
            if (machine.getStatus() == MachineStatus.MAINTENANCE) {
                continue;
            }
            List<MaintenanceRecord> history = repository.findByMachineOrderByScheduledDateDesc(machine);
            boolean alreadyBooked = history.stream()
                    .anyMatch(r -> r.getStatus() == MaintenanceStatus.SCHEDULED);
            if (alreadyBooked) {
                continue;
            }

            Optional<LocalDate> lastServiced = history.stream()
                    .filter(r -> r.getStatus() == MaintenanceStatus.COMPLETED)
                    .map(MaintenanceRecord::getCompletedDate)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder());

            if (lastServiced.isEmpty()) {
                due.add(new ServiceDue(machine, null, 0));
            } else if (lastServiced.get().isBefore(cutOff)) {
                long months = ChronoUnit.MONTHS.between(lastServiced.get(), LocalDate.now());
                due.add(new ServiceDue(machine, lastServiced.get(), months));
            }
        }
        return due;
    }

    public long countDueForService() {
        return dueForService().size();
    }

    /**
     * Sends the service-interval reminders to admins (and private owners).
     * Triggered by the admin from the maintenance screen.
     *
     * @return how many machines were flagged
     */
    @Transactional
    public int sendServiceReminders() {
        List<ServiceDue> due = dueForService();
        for (ServiceDue item : due) {
            alert(item.machine(), "Machine due for service",
                    item.machine().getModel() + " (" + item.machine().getRegistrationNumber()
                            + ") is due its " + SERVICE_INTERVAL_MONTHS
                            + "-monthly service. " + item.reason() + ".");
        }
        if (!due.isEmpty()) {
            auditService.record("MAINTENANCE_REMINDERS_SENT", "Machine", null,
                    due.size() + " machine(s) flagged as due for service");
        }
        return due.size();
    }

    // ---------- Reads ----------

    public List<MaintenanceRecord> all() {
        return repository.findAllByOrderByScheduledDateDesc();
    }

    public List<MaintenanceRecord> upcoming() {
        return repository.findByStatusOrderByScheduledDateAsc(MaintenanceStatus.SCHEDULED);
    }

    public List<MaintenanceRecord> completed() {
        return repository.findByStatusOrderByScheduledDateAsc(MaintenanceStatus.COMPLETED);
    }

    public List<MaintenanceRecord> historyFor(Long machineId) {
        return repository.findByMachineOrderByScheduledDateDesc(fleetService.getById(machineId));
    }

    public long countScheduled() {
        return repository.countByStatus(MaintenanceStatus.SCHEDULED);
    }

    public MaintenanceRecord getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Maintenance record", id));
    }

    /** Notifies every admin, plus the private owner when the machine is not ours. */
    private void alert(Machine machine, String title, String message) {
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            notificationService.notify(admin, title, message);
        }
        if (machine.getOwnership() == Ownership.PRIVATE && machine.getOwner() != null) {
            notificationService.notify(machine.getOwner(), title, message);
        }
    }
}
