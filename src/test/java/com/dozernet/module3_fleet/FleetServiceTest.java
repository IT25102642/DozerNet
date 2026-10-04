package com.dozernet.module3_fleet;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module3_fleet.dto.MachineForm;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module3_fleet.repository.MachineRepository;
import com.dozernet.module3_fleet.service.FleetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fleet rules: who can list a machine, when it becomes bookable, and what stops
 * a machine being deleted out from under a booking.
 */
@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    @Mock MachineRepository machineRepository;
    @Mock BookingRepository bookingRepository;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    @InjectMocks FleetService fleetService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User("Nimal", "n@x.lk", "0772222222", "hash", Role.OWNER);
        owner.setId(3L);
        lenient().when(machineRepository.save(any(Machine.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MachineForm form() {
        MachineForm f = new MachineForm();
        f.setModel("JCB 3CX");
        f.setType(MachineType.BACKHOE_LOADER);
        f.setRegistrationNumber("WP CAB-1234");
        f.setLocation("Colombo");
        f.setDailyRate(new BigDecimal("18500.00"));
        f.setImageUrl("/images/machines/backhoe-loader.jpg");
        return f;
    }

    @Test
    void companyMachinesAreBookableImmediately() {
        Machine machine = fleetService.createCompanyMachine(form());

        assertThat(machine.getOwnership()).isEqualTo(Ownership.COMPANY);
        assertThat(machine.isVerified()).isTrue();
        assertThat(machine.getStatus()).isEqualTo(MachineStatus.AVAILABLE);
        assertThat(machine.isBookable()).isTrue();
    }

    @Test
    void ownerListingsStayHiddenUntilAnAdminApprovesThem() {
        Machine machine = fleetService.createOwnerListing(owner, form());

        assertThat(machine.getOwnership()).isEqualTo(Ownership.PRIVATE);
        assertThat(machine.getOwner()).isEqualTo(owner);
        assertThat(machine.isVerified()).isFalse();
        assertThat(machine.isBookable()).isFalse();
    }

    @Test
    void approvingAListingPublishesItAndTellsTheOwner() {
        Machine machine = fleetService.createOwnerListing(owner, form());
        machine.setId(20L);
        when(machineRepository.findById(20L)).thenReturn(Optional.of(machine));

        fleetService.approveListing(20L);

        assertThat(machine.isVerified()).isTrue();
        assertThat(machine.isBookable()).isTrue();
        verify(notificationService).notify(eq(owner), eq("Listing approved"), anyString());
        verify(auditService).record(eq("LISTING_APPROVED"), eq("Machine"), eq(20L), anyString());
    }

    @Test
    void cannotDeleteAMachineThatHasBookings() {
        Machine machine = fleetService.createCompanyMachine(form());
        machine.setId(21L);
        when(machineRepository.findById(21L)).thenReturn(Optional.of(machine));
        when(bookingRepository.existsByMachine(machine)).thenReturn(true);

        assertThatThrownBy(() -> fleetService.delete(21L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot delete");

        verify(machineRepository, never()).delete(any());
    }

    @Test
    void cannotRejectAListingThatAlreadyHasBookings() {
        Machine machine = fleetService.createOwnerListing(owner, form());
        machine.setId(22L);
        when(machineRepository.findById(22L)).thenReturn(Optional.of(machine));
        when(bookingRepository.existsByMachine(machine)).thenReturn(true);

        assertThatThrownBy(() -> fleetService.rejectListing(22L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has bookings");
    }

    @Test
    void aMachineInMaintenanceIsNotBookable() {
        Machine machine = fleetService.createCompanyMachine(form());
        machine.setId(23L);
        when(machineRepository.findById(23L)).thenReturn(Optional.of(machine));

        fleetService.setStatus(23L, MachineStatus.MAINTENANCE);

        assertThat(machine.isBookable()).isFalse();
        verify(auditService).record(eq("MACHINE_STATUS_CHANGED"), eq("Machine"), eq(23L), anyString());
    }

    @Test
    void refusesADuplicateRegistrationNumberOnUpdate() {
        Machine machine = fleetService.createCompanyMachine(form());
        machine.setId(24L);
        when(machineRepository.findById(24L)).thenReturn(Optional.of(machine));

        MachineForm changed = form();
        changed.setRegistrationNumber("WP CAB-9999");
        when(machineRepository.existsByRegistrationNumber("WP CAB-9999")).thenReturn(true);

        assertThatThrownBy(() -> fleetService.update(24L, changed))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }
}
