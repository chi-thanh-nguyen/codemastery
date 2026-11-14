package com.codemastery.common.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Explicit application rejection using the approved 400/409 mappings.
 * The diagnostic message is never copied into a public response. Consumers must
 * translate a known conflict themselves; arbitrary database failures are not 409s.
 * A contract may fix a public detail (a constant chosen by code, never input or
 * diagnostic text); otherwise the shared CT18 status detail applies.
 */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    private final String publicDetail;

    public BusinessRuleException(String diagnosticMessage) {
        this(HttpStatus.BAD_REQUEST, diagnosticMessage);
    }

    public BusinessRuleException(HttpStatus status, String diagnosticMessage) {
        this(status, diagnosticMessage, null);
    }

    private BusinessRuleException(HttpStatus status, String diagnosticMessage, String publicDetail) {
        super(diagnosticMessage);
        if (status != HttpStatus.BAD_REQUEST && status != HttpStatus.CONFLICT) {
            throw new IllegalArgumentException("Business rule status must be 400 or 409");
        }
        if (publicDetail != null && publicDetail.isBlank()) {
            throw new IllegalArgumentException("A contract public detail must not be blank");
        }
        this.status = status;
        this.publicDetail = publicDetail;
    }

    /** 400 rejection whose public detail is fixed by an approved contract (e.g. CT02 generic reset error). */
    public static BusinessRuleException withPublicDetail(String diagnosticMessage, String contractPublicDetail) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, diagnosticMessage,
                Objects.requireNonNull(contractPublicDetail));
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Contract-fixed public detail, or {@code null} to use the shared status detail. */
    public String getPublicDetail() {
        return publicDetail;
    }
}
