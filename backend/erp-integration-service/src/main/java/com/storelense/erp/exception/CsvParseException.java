package com.storelense.erp.exception;

public class CsvParseException extends RuntimeException {

    private final int lineNumber;

    public CsvParseException(String message, int lineNumber) {
        super("Line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public CsvParseException(String message, int lineNumber, Throwable cause) {
        super("Line " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() { return lineNumber; }
}
