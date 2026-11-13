package com.codemastery.modules.auth.infrastructure.email;

import com.codemastery.common.config.AppConfig.PasswordResetActivation;
import com.codemastery.common.config.AppConfig.PasswordResetSettings;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailDelivery;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailMessage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Plain-text Auth recovery delivery over authenticated SMTP submission with mandatory
 * STARTTLS and server identity verification. The transport policy is fixed here rather
 * than exposed as {@code spring.mail.*} settings, so configuration cannot select a
 * plaintext fallback, and no Spring Boot mail sender/health contributor is registered.
 * When recovery is inactive no sender exists and nothing can be sent.
 */
@Component
public class SmtpPasswordResetEmailAdapter implements ResetEmailDelivery {

    static final int SUBMISSION_PORT = 587;
    static final int NETWORK_TIMEOUT_MILLIS = 5000;
    static final String SUBJECT = "Reset your CodeMastery password";

    private final JavaMailSender mail;
    private final String sender;

    @Autowired
    public SmtpPasswordResetEmailAdapter(PasswordResetActivation activation) {
        this(activation.isActive() ? newMailSender(activation.settings()) : null,
                activation.isActive() ? activation.settings().emailFrom() : null);
    }

    /** Explicit seam for service tests; both arguments are null only for inactive recovery. */
    public SmtpPasswordResetEmailAdapter(JavaMailSender mail, String sender) {
        if ((mail == null) != (sender == null)) {
            throw new IllegalArgumentException("Mail sender and sender address must both be present or absent");
        }
        this.mail = mail;
        this.sender = sender;
    }

    /** Builds the fixed-policy SMTP submission client from validated recovery settings. */
    public static JavaMailSenderImpl newMailSender(PasswordResetSettings settings) {
        Objects.requireNonNull(settings);
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setProtocol("smtp");
        sender.setHost(settings.smtpHost());
        sender.setPort(SUBMISSION_PORT);
        sender.setUsername(settings.smtpUsername());
        sender.setPassword(settings.smtpPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", Integer.toString(NETWORK_TIMEOUT_MILLIS));
        properties.setProperty("mail.smtp.timeout", Integer.toString(NETWORK_TIMEOUT_MILLIS));
        properties.setProperty("mail.smtp.writetimeout", Integer.toString(NETWORK_TIMEOUT_MILLIS));
        sender.setJavaMailProperties(properties);
        return sender;
    }

    @Override
    public void send(ResetEmailMessage reset) {
        if (mail == null) {
            throw new IllegalStateException("Password recovery delivery is inactive");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(reset.recipient());
        message.setSubject(SUBJECT);
        message.setText("A password reset was requested for your CodeMastery account.\n\n"
                + "If you requested this, use the link below. It expires 30 minutes after it was issued:\n\n"
                + reset.resetLink()
                + "\n\nIf you did not request this, you can ignore this email.");
        mail.send(message);
    }
}
