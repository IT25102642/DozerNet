package com.dozernet.module2_booking.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.document.Document;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.document.DocumentType;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.dto.ReturnInspectionForm;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.entity.ResponsibleParty;
import com.dozernet.module2_booking.entity.ReturnInspection;
import com.dozernet.module2_booking.repository.ReturnInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Records the condition of a machine when it comes back from a completed job.
 * Filing the report - notes, any damage, who is responsible, an optional photo -
 * is what turns "the customer says it was already scratched" into a documented
 * fact.
 */
@Service
public class ReturnInspectionService {

    private final ReturnInspectionRepository inspectionRepository;
    private final BookingService bookingService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public ReturnInspectionService(ReturnInspectionRepository inspectionRepository,
                                   BookingService bookingService,
                                   DocumentService documentService,
                                   NotificationService notificationService,
                                   CurrentUserService currentUserService,
                                   AuditService auditService) {
        this.inspectionRepository = inspectionRepository;
        this.bookingService = bookingService;
        this.documentService = documentService;
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public ReturnInspection record(Long bookingId, ReturnInspectionForm form, MultipartFile photo) {
        Booking booking = bookingService.getById(bookingId);

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "A return inspection can only be filed once the job is marked completed.");
        }
        if (inspectionRepository.existsByBooking(booking)) {
            throw new BusinessRuleException("This booking already has a return inspection on file.");
        }
        if (form.isDamageReported()) {
            if (form.getDamageDescription() == null || form.getDamageDescription().isBlank()) {
                throw new BusinessRuleException("Describe the damage that was found.");
            }
            if (form.getResponsibleParty() == null || form.getResponsibleParty() == ResponsibleParty.NONE) {
                throw new BusinessRuleException("Select who is responsible for the damage.");
            }
        }

        User inspector = currentUserService.current().orElse(null);
        ReturnInspection inspection = new ReturnInspection(booking, inspector);
        inspection.setNotes(form.getNotes() == null ? "" : form.getNotes().trim());
        inspection.setDamageReported(form.isDamageReported());
        inspection.setResponsibleParty(form.isDamageReported()
                ? form.getResponsibleParty() : ResponsibleParty.NONE);
        inspection.setDamageDescription(form.isDamageReported()
                ? form.getDamageDescription().trim() : null);
        inspection.setEstimatedRepairCost(form.isDamageReported() ? form.getEstimatedRepairCost() : null);

        if (inspector != null) {
            Document stored = documentService.store(inspector, DocumentType.INSPECTION_PHOTO, photo);
            inspection.setPhoto(stored);
        }

        ReturnInspection saved = inspectionRepository.save(inspection);

        auditService.record("RETURN_INSPECTION_FILED", "Booking", booking.getId(),
                form.isDamageReported()
                        ? "Damage reported - responsible: " + inspection.getResponsibleParty().getDisplayName()
                        : "Returned with no damage");

        notificationService.notify(booking.getCustomer(), "Return inspection completed",
                "The " + booking.getMachine().getModel() + " you hired has been inspected on return. "
                        + (form.isDamageReported()
                        ? "Damage was recorded: " + inspection.getDamageDescription()
                        : "No damage was found. Thank you for looking after the machine."));

        return saved;
    }

    public Optional<ReturnInspection> forBooking(Booking booking) {
        return inspectionRepository.findByBooking(booking);
    }

    public ReturnInspection getById(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Return inspection", id));
    }

    public List<ReturnInspection> all() {
        return inspectionRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Inspections where damage was found - the damage-tracking view. */
    public List<ReturnInspection> withDamage() {
        return inspectionRepository.findByDamageReportedTrueOrderByCreatedAtDesc();
    }
}
