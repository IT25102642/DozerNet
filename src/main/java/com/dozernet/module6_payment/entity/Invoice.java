package com.dozernet.module6_payment.entity;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An invoice raised for a booking. One invoice per booking.
 *
 * <p>The total is itemised: hire charge, district haulage, then VAT on both.
 * {@code amount} always holds the grand total the customer owes, so the balance
 * and payment logic work off a single figure.</p>
 */
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User customer;

    /** Human-readable reference, e.g. INV-2026-00001. */
    @Column(unique = true, length = 20)
    private String invoiceNumber;

    /** Hire charge before haulage and VAT. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount = BigDecimal.ZERO;

    /** District-based transport charge. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal transportSurcharge = BigDecimal.ZERO;

    /** VAT charged on hire + transport. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    /** Grand total payable (hire + transport + VAT). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** DozerNet's commission on a privately owned machine; zero otherwise. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal ownerCommission = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate issuedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    public Invoice() {
    }

    public Invoice(Booking booking, User customer, BigDecimal amount) {
        this.booking = booking;
        this.customer = customer;
        this.amount = amount;
        this.baseAmount = amount;
        this.issuedDate = LocalDate.now();
    }

    public BigDecimal getBalance() {
        return amount.subtract(amountPaid);
    }

    /** Hire + haulage, i.e. the figure VAT is charged on. */
    public BigDecimal getSubTotal() {
        return baseAmount.add(transportSurcharge);
    }

    /** What a private owner receives once commission is deducted. */
    public BigDecimal getOwnerPayout() {
        return baseAmount.subtract(ownerCommission);
    }

    /** Reference for display, falling back to the id for legacy rows. */
    public String getReference() {
        return invoiceNumber == null || invoiceNumber.isBlank() ? "#" + getId() : invoiceNumber;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getTransportSurcharge() {
        return transportSurcharge;
    }

    public void setTransportSurcharge(BigDecimal transportSurcharge) {
        this.transportSurcharge = transportSurcharge;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public BigDecimal getOwnerCommission() {
        return ownerCommission;
    }

    public void setOwnerCommission(BigDecimal ownerCommission) {
        this.ownerCommission = ownerCommission;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public void setIssuedDate(LocalDate issuedDate) {
        this.issuedDate = issuedDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }
}
