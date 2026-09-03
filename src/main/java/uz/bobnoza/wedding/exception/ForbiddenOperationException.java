package uz.bobnoza.wedding.exception;

/** Thrown when an admin attempts to access or modify another admin's guests. */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
