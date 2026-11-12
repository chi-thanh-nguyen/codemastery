package com.codemastery.modules.auth.infrastructure.email;

import com.codemastery.common.config.AppConfig.PasswordResetSettings;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailDelivery;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text Auth recovery delivery over the configured authenticated STARTTLS SMTP channel. */
@Component
public class SmtpPasswordResetEmailAdapter implements ResetEmailDelivery {

    private final JavaMailSender mail;
    private final PasswordResetSettings settings;

    public SmtpPasswordResetEmailAdapter(JavaMailSender mail, PasswordResetSettings settings) {
        this.mail = mail;
        this.settings = settings;
    }

    @Override
    public void send(ResetEmailMessage reset) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.emailFrom());
        message.setTo(reset.recipient());
        message.setSubject("Reset your CodeMastery password");
        message.setText("A password reset was requested for your CodeMastery account.\n\n"
                + "If you requested this, use the link below. It expires 30 minutes after it was issued:\n\n"
                + reset.resetLink()
                + "\n\nIf you did not request this, you can ignore this email.");
        mail.send(message);
    }
}
