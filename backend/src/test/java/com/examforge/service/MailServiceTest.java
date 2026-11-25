package com.examforge.service;

import com.examforge.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailServiceTest {

    @SuppressWarnings("unchecked")
    private MailService service(boolean enabled, JavaMailSender sender) {
        AppProperties props = new AppProperties(null, null, null, null,
                new AppProperties.Mail(enabled, "ExamForge <no-reply@examforge.test>"), null, "http://localhost:5173");
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sender);
        return new MailService(props, provider);
    }

    @Test
    void sendsVerificationLinkThroughSmtpWhenEnabled() {
        JavaMailSender sender = mock(JavaMailSender.class);
        service(true, sender).sendVerificationEmail("user@example.com", "Asha", "tok123");

        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(msg.capture());
        assertThat(msg.getValue().getTo()).containsExactly("user@example.com");
        assertThat(msg.getValue().getFrom()).isEqualTo("ExamForge <no-reply@examforge.test>");
        assertThat(msg.getValue().getText()).contains("http://localhost:5173/verify-email?token=tok123");
    }

    @Test
    void onlyLogsWhenDisabled() {
        JavaMailSender sender = mock(JavaMailSender.class);
        service(false, sender).sendPasswordResetEmail("user@example.com", "Asha", "tok123");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpFailureDoesNotThrow() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        service(true, sender).sendVerificationEmail("user@example.com", "Asha", "tok123");
    }
}
