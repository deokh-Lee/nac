package com.saltlux.nac.elecdoc;

public class DocumentExtractionException extends RuntimeException {

    private final String errorCode;

    public DocumentExtractionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
