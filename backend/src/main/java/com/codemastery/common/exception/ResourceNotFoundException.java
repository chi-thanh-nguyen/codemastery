package com.codemastery.common.exception;

/** A public missing/unavailable resource; its diagnostic identity is not exposed. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String diagnosticMessage) {
        super(diagnosticMessage);
    }
}
