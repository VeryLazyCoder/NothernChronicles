package ru.practicum.gwint_backend.domain.error;

public class RuleViolationException extends RuntimeException{
    private final ErrorCode errorCode;
    public RuleViolationException(ErrorCode errorCode, String message){
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
