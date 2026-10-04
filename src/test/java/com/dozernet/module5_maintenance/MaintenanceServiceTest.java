package com.dozernet.module5_maintenance;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module5_maintenance.dto.ScheduleForm;
import com.dozernet.module5_maintenance.entity.MaintenanceRecord;
import com.dozernet.module5_maintenance.entity.MaintenanceStatus;
import com.dozernet.module5_maintenance.repository.MaintenanceRecordRepository;
import com.dozernet.module5_maintenance.service.MaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Maintenance rules: scheduling takes a machine off hire, completion can put it
 * back, and anything unserviced for three months is flagged.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock MaintenanceRecordRepository repository;
    @Mock FleetService fleetService;
    @Mock NotificationService notificationService;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;

    @InjectMocks MaintenanceService maintenanceService;

    private Machine machine;

    @BeforeEach
    void setUp() {
        machine = new Machine();
        machine.setId(10L);
        machine.setModel("JCB 3CX");
        machine.setType(MachineType.BACKHOE_LOADER);
        machine.setRegistrationNumber("WP CAB-3421");
        machine.setDailyRate(new BigDecimal("18500.00"));
        machine.setStatus(MachineStatus.AVAILABLE);
        machine.setOwnership(Ownership.COMPANY);

        lenient().when(fleetService.getById(10L)).thenReturn(machine);
        lenient().when(repository.save(any(MaintenanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());
    }

    private static ScheduleForm scheduleForm() {
        ScheduleForm form = new ScheduleForm();
        form.setMachineId(10L);
        form.setScheduledDate(LocalDate.now().plusDays(2));
        form.setDescription("250-hour service");
        return form;
    }

    @Test
    void schedulingTakesTheMachineOffHire() {
        MaintenanceRecord record = maintenanceService.schedule(scheduleForm());

        assertThat(record.getStatus()).isEqualTo(MaintenanceStatus.SCHEDULED);
        verify(fleetService).setStatus(10L, MachineStatus.MAINTENANCE);
        verify(auditService).record(eq("MAINTENANCE_SCHEDULED"), eq("Machine"), eq(10L), anyString());
    }

    @Test
    void schedulingAlertsThePrivateOwnerToo() {
        User owner = new User("Nimal", "n@x.lk", "0772222222", "hash", Role.OWNER);
        owner.setId(3L);
        machine.setOwnership(Ownership.PRIVATE);
        machine.setOwner(owner);

        maintenanceService.schedule(scheduleForm());

        verify(notificationService).notify(eq(owner), eq("Maintenance scheduled"), anyString());
    }

    @Test
    void completingPutsTheMachineBackWhenAsked() {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(60L);
        record.setMachine(machine);
        record.setScheduledDate(LocalDate.now());
        record.setStatus(MaintenanceStatus.SCHEDULED);
        when(repository.findById(60L)).thenReturn(Optional.of(record));

        maintenanceService.complete(60L, "Filters, hydraulic oil", new BigDecimal("42000.00"), true);

        assertThat(record.getStatus()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(record.getCompletedDate()).isEqualTo(LocalDate.now());
        assertThat(record.getCost()).isEqualByComparingTo("42000.00");
        verify(fleetService).setStatus(10L, MachineStatus.AVAILABLE);
    }

    @Test
    void completingLeavesTheMachineOutOfServiceWhenNotReturned() {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(61L);
        record.setMachine(machine);
        record.setStatus(MaintenanceStatus.SCHEDULED);
        when(repository.findById(61L)).thenReturn(Optional.of(record));

        maintenanceService.complete(61L, "Awaiting parts", null, false);

        verify(fleetService, org.mockito.Mockito.never()).setStatus(10L, MachineStatus.AVAILABLE);
    }

    @Test
    void flagsAMachineThatHasNeverBeenServiced() {
        when(fleetService.findAll()).thenReturn(List.of(machine));
        when(repository.findByMachineOrderByScheduledDateDesc(machine)).thenReturn(List.of());

        List<MaintenanceService.ServiceDue> due = maintenanceService.dueForService();

        assertThat(due).hasSize(1);
        assertThat(due.get(0).neverServiced()).isTrue();
        assertThat(due.get(0).reason()).contains("No service recorded");
    }

    @Test
    void flagsAMachineServicedLongerAgoThanTheInterval() {
        MaintenanceRecord old = new MaintenanceRecord();
        old.setMachine(machine);
        old.setStatus(MaintenanceStatus.COMPLETED);
        old.setCompletedDate(LocalDate.now().minusMonths(5));
        when(fleetService.findAll()).thenReturn(List.of(machine));
        when(repository.findByMachineOrderByScheduledDateDesc(machine)).thenReturn(List.of(old));

        List<MaintenanceService.ServiceDue> due = maintenanceService.dueForService();

        assertThat(due).hasSize(1);
        assertThat(due.get(0).monthsSince()).isEqualTo(5);
    }

    @Test
    void leavesARecentlyServicedMachineAlone() {
        MaintenanceRecord recent = new MaintenanceRecord();
        recent.setMachine(machine);
        recent.setStatus(MaintenanceStatus.COMPLETED);
        recent.setCompletedDate(LocalDate.now().minusMonths(1));
        when(fleetService.findAll()).thenReturn(List.of(machine));
        when(repository.findByMachineOrderByScheduledDateDesc(machine)).thenReturn(List.of(recent));

        assertThat(maintenanceService.dueForService()).isEmpty();
    }

    @Test
    void doesNotChaseAMachineThatAlreadyHasServiceBookedIn() {
        MaintenanceRecord booked = new MaintenanceRecord();
        booked.setMachine(machine);
        booked.setStatus(MaintenanceStatus.SCHEDULED);
        booked.setScheduledDate(LocalDate.now().plusDays(3));
        when(fleetService.findAll()).thenReturn(List.of(machine));
        when(repository.findByMachineOrderByScheduledDateDesc(machine)).thenReturn(List.of(booked));

        assertThat(maintenanceService.dueForService()).isEmpty();
    }

    @Test
    void skipsMachinesAlreadyInTheWorkshop() {
        machine.setStatus(MachineStatus.MAINTENANCE);
        when(fleetService.findAll()).thenReturn(List.of(machine));

        assertThat(maintenanceService.dueForService()).isEmpty();
    }

    @Test
    void sendsAReminderForEachOverdueMachine() {
        User admin = new User("Admin", "a@x.lk", "0770000000", "hash", Role.ADMIN);
        admin.setId(2L);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(fleetService.findAll()).thenReturn(List.of(machine));
        when(repository.findByMachineOrderByScheduledDateDesc(machine)).thenReturn(List.of());

        int flagged = maintenanceService.sendServiceReminders();

        assertThat(flagged).isEqualTo(1);
        verify(notificationService).notify(eq(admin), eq("Machine due for service"), anyString());
    }
}
