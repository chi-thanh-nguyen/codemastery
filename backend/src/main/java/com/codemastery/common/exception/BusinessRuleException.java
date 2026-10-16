package com.codemastery.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Explicit application rejection using the approved 400/409 mappings.
 * The diagnostic message is never copied into a public response. Consumers must
 * translate a known conflict themselves; arbitrary database failures are not 409s.
 */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(String diagnosticMessage) {
        this(HttpStatus.BAD_REQUEST, diagnosticMessage);
    }

    public BusinessRuleException(HttpStatus status, String diagnosticMessage) {
        super(diagnosticMessage);
        if (status != HttpStatus.BAD_REQUEST && status != HttpStatus.CONFLICT) {
            throw new IllegalArgumentException("Business rule status must be 400 or 409");
        }
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
