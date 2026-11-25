package com.examforge.service;

import com.examforge.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text emails in the background (SMTP can take seconds; requests shouldn't wait).
 * When {@code app.mail.enabled=false} (local development) the message is written to the log instead,
 * so verification and reset links can be copied from the console.
 */
@Service
@RequiredArgsConstructor
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final AppProperties props;
    private final ObjectProvider<JavaMailSender> mailSender;

    @EventListener(ApplicationReadyEvent.class)
    void logMode() {
        if (props.mail().enabled()) {
            log.info("Email sending is ON (from: {})", props.mail().from());
        } else {
            log.warn("Email sending is OFF (MAIL_ENABLED=false). Verification/reset links are printed to this log.");
        }
    }

    public void send(String to, String subject, String body) {
        if (!props.mail().enabled()) {
            log.info("\n===== [DEV MAIL — not sent] =====\nTo: {}\nSubject: {}\n\n{}\n=================================",
                    to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            System.out.println(props.mail().from());
            msg.setFrom(props.mail().from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.getObject().send(msg);
            log.info("Sent email '{}'", subject);
        } catch (Exception e) {
            // Don't fail the user's request because SMTP is down; the user can ask for a new email.
            log.error("Failed to send email '{}': {}", subject, e.getMessage());
        }
    }

    // @Async goes on these public entry points: Spring proxies don't intercept calls within the class.
    @Async
    public void sendVerificationEmail(String to, String name, String token) {
        String link = props.frontendUrl() + "/verify-email?token=" + token;
        send(to, "Verify your ExamForge email", """
                Hi %s,

                Welcome to ExamForge! Please confirm your email address by opening this link:

                %s

                The link is valid for 24 hours. If you did not create an account, ignore this email.
                """.formatted(name, link));
    }

    @Async
    public void sendPasswordResetEmail(String to, String name, String token) {
        String link = props.frontendUrl() + "/reset-password?token=" + token;
        send(to, "Reset your ExamForge password", """
                Hi %s,

                We received a request to reset your password. Open this link to choose a new one:

                %s

                The link is valid for 30 minutes and can be used once. If you did not request this, ignore this email.
                """.formatted(name, link));
    }
}
