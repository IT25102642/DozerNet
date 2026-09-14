package com.dozernet.module3_fleet.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module3_fleet.dto.MachineForm;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module3_fleet.repository.MachineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Fleet (JCB) Management business logic: company machine CRUD, private owner
 * listings, admin verification, availability status and the public search.
 */
@Service
public class FleetService {

    private final MachineRepository machineRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public FleetService(MachineRepository machineRepository,
                        BookingRepository bookingRepository,
                        NotificationService notificationService,
                        AuditService auditService) {
        this.machineRepository = machineRepository;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ---------- Read / search ----------

    public List<Machine> search(MachineType type, String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return machineRepository.search(type, kw);
    }

    /** Catalogue search limited to any of the given types (job-category browse). */
    public List<Machine> searchByTypes(Collection<MachineType> types, String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        if (types == null || types.isEmpty()) {
            return machineRepository.search(null, kw);
        }
        return machineRepository.searchByTypes(types, kw);
    }

    public List<Machine> findAll() {
        return machineRepository.findAllWithOwner();
    }

    public List<Machine> findByOwner(User owner) {
        return machineRepository.findByOwnerWithOwner(owner);
    }

    public List<Machine> pendingApprovals() {
        return machineRepository.findUnverifiedWithOwner();
    }

    public Machine getById(Long id) {
        return machineRepository.findByIdWithOwner(id)
                .or(() -> machineRepository.findById(id))
                .orElseThrow(() -> ResourceNotFoundException.of("Machine", id));
    }

    /** Ensures the machine belongs to the owner (safe with open-in-view=false). */
    @Transactional(readOnly = true)
    public Machine requireOwnedBy(Long id, User owner) {
        Machine m = getById(id);
        if (m.getOwner() == null || !m.getOwner().getId().equals(owner.getId())) {
            throw new BusinessRuleException("You can only manage your own machine listings.");
        }
        return m;
    }

    // ---------- Create / update / delete ----------

    /** Admin adds a company-owned machine (verified immediately). */
    @Transactional
    public Machine createCompanyMachine(MachineForm form) {
        Machine m = new Machine();
        apply(form, m);
        m.setOwnership(Ownership.COMPANY);
        m.setOwner(null);
        m.setVerified(true);
        m.setStatus(MachineStatus.AVAILABLE);
        return machineRepository.save(m);
    }

    /** Private owner lists a machine (unverified until an admin approves it). */
    @Transactional
    public Machine createOwnerListing(User owner, MachineForm form) {
        Machine m = new Machine();
        apply(form, m);
        m.setOwnership(Ownership.PRIVATE);
        m.setOwner(owner);
        m.setVerified(false);
        m.setStatus(MachineStatus.AVAILABLE);
        return machineRepository.save(m);
    }

    @Transactional
    public Machine update(Long id, MachineForm form) {
        Machine m = getById(id);
        String newReg = form.getRegistrationNumber().trim();
        if (!newReg.equalsIgnoreCase(m.getRegistrationNumber())
                && machineRepository.existsByRegistrationNumber(newReg)) {
            throw new BusinessRuleException("A machine with that registration number already exists");
        }
        apply(form, m);
        return machineRepository.save(m);
    }

    @Transactional
    public void delete(Long id) {
        Machine m = getById(id);
        if (bookingRepository.existsByMachine(m)) {
            throw new BusinessRuleException(
                    "Cannot delete a machine that has bookings. Mark it unavailable instead.");
        }
        machineRepository.delete(m);
    }

    // ---------- Verification ----------

    @Transactional
    public void approveListing(Long id) {
        Machine m = getById(id);
        m.setVerified(true);
        machineRepository.save(m);
        auditService.record("LISTING_APPROVED", "Machine", m.getId(),
                m.getModel() + " (" + m.getRegistrationNumber() + ") approved for hire");
        if (m.getOwner() != null) {
            notificationService.notify(m.getOwner(), "Listing approved",
                    "Your machine \"" + m.getModel() + "\" has been approved and is now bookable.");
        }
    }

    @Transactional
    public void rejectListing(Long id) {
        Machine m = getById(id);
        if (bookingRepository.existsByMachine(m)) {
            throw new BusinessRuleException("Cannot reject/delete a listing that already has bookings.");
        }
        User owner = m.getOwner();
        String model = m.getModel();
        String registration = m.getRegistrationNumber();
        machineRepository.delete(m);
        auditService.record("LISTING_REJECTED", "Machine", id,
                model + " (" + registration + ") rejected and removed");
        if (owner != null) {
            notificationService.notify(owner, "Listing rejected",
                    "Your machine listing \"" + model + "\" was not approved. Please contact the administrator.");
        }
    }

    // ---------- Availability status ----------

    @Transactional
    public void setStatus(Long id, MachineStatus status) {
        Machine m = getById(id);
        MachineStatus previous = m.getStatus();
        m.setStatus(status);
        machineRepository.save(m);
        if (previous != status) {
            auditService.record("MACHINE_STATUS_CHANGED", "Machine", m.getId(),
                    m.getModel() + ": " + previous.getDisplayName() + " -> " + status.getDisplayName());
        }
    }

    private void apply(MachineForm form, Machine m) {
        String reg = form.getRegistrationNumber().trim();
        if (m.getId() == null && machineRepository.existsByRegistrationNumber(reg)) {
            throw new BusinessRuleException("A machine with that registration number already exists");
        }
        m.setModel(form.getModel().trim());
        m.setType(form.getType());
        m.setRegistrationNumber(reg);
        m.setLocation(form.getLocation().trim());
        m.setDailyRate(form.getDailyRate());
        m.setDescription(form.getDescription());
        m.setImageUrl(form.getImageUrl());
    }
}