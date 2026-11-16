package com.codemastery.modules.auth.infrastructure.email;

import com.codemastery.common.config.AppConfig.PasswordResetActivation;
import com.codemastery.common.config.AppConfig.PasswordResetSettings;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailDelivery;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailMessage;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.eclipse.angus.mail.smtp.SMTPTransport;
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

    // Angus 2.0.5's SMTP, wire, socket and NTLM helper diagnostics use these JUL loggers,
    // including when Boot forwards them to SLF4J. Keep strong references: JUL otherwise
    // permits a logger (and its security policy) to be garbage-collected.
    private static final List<java.util.logging.Logger> SMTP_DIAGNOSTICS = List.of(
            java.util.logging.Logger.getLogger("org.eclipse.angus.mail.smtp"),
            java.util.logging.Logger.getLogger("org.eclipse.angus.mail.smtp.protocol"),
            java.util.logging.Logger.getLogger("org.eclipse.angus.mail.util.socket"),
            java.util.logging.Logger.getLogger("org.eclipse.angus.mail.auth"));
    private static final PrintStream NO_DIAGNOSTIC_OUTPUT = new PrintStream(OutputStream.nullOutputStream());

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
        // Block at the source, before handlers/bridges see a record. A filter is needed
        // as well as OFF: a later DEBUG/TRACE level change must not enable wire logging.
        // These named Angus sources are silenced JVM-wide; application loggers are untouched.
        SMTP_DIAGNOSTICS.forEach(logger -> {
            logger.setLevel(java.util.logging.Level.OFF);
            logger.setFilter(record -> false);
        });
        JavaMailSenderImpl sender = new AuthenticatedSubmissionSender();
        sender.setProtocol("smtp");
        sender.setHost(settings.smtpHost());
        sender.setPort(SUBMISSION_PORT);
        sender.setUsername(settings.smtpUsername());
        sender.setPassword(settings.smtpPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = new Properties();
        properties.setProperty("mail.debug", "false");
        properties.setProperty("mail.debug.auth", "false");
        properties.setProperty("mail.debug.auth.username", "false");
        properties.setProperty("mail.debug.auth.password", "false");
        properties.setProperty("mail.smtp.auth", "true");
        // Username/password submission over verified TLS needs neither legacy challenge-response
        // mechanisms nor OAuth/SASL. A server offering only excluded mechanisms must fail closed.
        properties.setProperty("mail.smtp.auth.mechanisms", "LOGIN PLAIN");
        properties.setProperty("mail.smtp.sasl.enable", "false");
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

    /**
     * Makes SMTP authentication mandatory. With {@code mail.smtp.auth=true}, Jakarta Mail still
     * treats the connection as usable when the server's post-STARTTLS EHLO does not offer AUTH, and
     * it would then submit the message unauthenticated. When AUTH is offered, Jakarta Mail
     * authenticates during connect and fails on rejection, so a connected transport that offered
     * AUTH has authenticated. Any other connection is closed before MAIL FROM.
     */
    static final class AuthenticatedSubmissionSender extends JavaMailSenderImpl {
        @Override
        public synchronized Session getSession() {
            Session session = super.getSession();
            // Session debug bypasses JUL/SLF4J. Apply to every session, including one
            // recreated when test-only trust properties are installed.
            session.setDebugOut(NO_DIAGNOSTIC_OUTPUT);
            session.setDebug(false);
            return session;
        }

        @Override
        protected Transport connectTransport() throws MessagingException {
            Transport transport = super.connectTransport();
            if (transport instanceof SMTPTransport smtp && getUsername() != null && getPassword() != null
                    && (smtp.supportsExtension("AUTH") || smtp.supportsExtension("AUTH=LOGIN"))) {
                return transport;
            }
            try {
                transport.close();
            } catch (MessagingException ignored) {
                // The refusal below is the outcome; a failed QUIT adds nothing.
            }
            throw new AuthenticationFailedException("SMTP server did not offer authentication after STARTTLS");
        }
    }
}
