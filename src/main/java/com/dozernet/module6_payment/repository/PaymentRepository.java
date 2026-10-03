package com.dozernet.module6_payment.repository;

import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceOrderByPaidDateDesc(Invoice invoice);
}
