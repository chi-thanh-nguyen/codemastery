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

    private final String validationField;

    public BusinessRuleException(String diagnosticMessage) {
        this(HttpStatus.BAD_REQUEST, diagnosticMessage);
    }

    public BusinessRuleException(HttpStatus status, String diagnosticMessage) {
        this(status, diagnosticMessage, null);
    }

    private BusinessRuleException(HttpStatus status, String diagnosticMessage, String publicDetail) {
        this(status, diagnosticMessage, publicDetail, null);
    }

    private BusinessRuleException(HttpStatus status, String diagnosticMessage, String publicDetail, String validationField) {
        super(diagnosticMessage);
        if (status != HttpStatus.BAD_REQUEST && status != HttpStatus.CONFLICT) {
            throw new IllegalArgumentException("Business rule status must be 400 or 409");
        }
        if (publicDetail != null && publicDetail.isBlank()) {
            throw new IllegalArgumentException("A contract public detail must not be blank");
        }
        this.status = status;
        this.publicDetail = publicDetail;
        this.validationField = validationField;
    }

    /** 400 rejection whose public detail is fixed by an approved contract (e.g. CT02 generic reset error). */
    public static BusinessRuleException withPublicDetail(String diagnosticMessage, String contractPublicDetail) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, diagnosticMessage,
                Objects.requireNonNull(contractPublicDetail));
    }

    /** CT18 field error with a code-owned identifier; no rejected value or diagnostic is exposed. */
    public static BusinessRuleException invalidField(String field) {
        if (field == null || !field.matches("[a-zA-Z][a-zA-Z0-9]*")) throw new IllegalArgumentException("Invalid field identifier");
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid " + field, null, field);
    }

    public String getValidationField() { return validationField; }

    public HttpStatus getStatus() {
        return status;
    }

    /** Contract-fixed public detail, or {@code null} to use the shared status detail. */
    public String getPublicDetail() {
        return publicDetail;
    }
}
