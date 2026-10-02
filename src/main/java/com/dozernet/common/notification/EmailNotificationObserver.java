package com.dozernet.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Observer that "sends" an email. For this academic build the message is
 * written to the console as a fully formatted email rather than posted to a
 * real SMTP server, which keeps the demo self-contained while still showing
 * exactly what each recipient would receive. Swapping in JavaMailSender later
 * requires no caller changes.
 */
@Component
public class EmailNotificationObserver implements NotificationObserver {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationObserver.class);
    private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Value("${dozernet.mail.from:no-reply@dozernet.lk}")
    private String fromAddress;

    @Override
    public void onEvent(NotificationEvent event) {
        String body = """

                ==================== DozerNet e-mail ====================
                From:    DozerNet <%s>
                To:      %s <%s>
                Sent:    %s
                Subject: %s
                ---------------------------------------------------------
                Dear %s,

                %s

                Sign in to DozerNet to view the full details.

                Kind regards,
                DozerNet Operations
                =========================================================
                """.formatted(
                fromAddress,
                event.recipient().getFullName(),
                event.recipient().getEmail(),
                LocalDateTime.now().format(SENT_AT),
                event.title(),
                event.recipient().getFullName(),
                event.message());

        log.info(body);
    }
}
